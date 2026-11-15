package vql.typed

import lexer.VariableText
import parser.SourcePosition
import vql.error.QueryError
import vql.error.QueryError.{EvaluationFailure, ValidationProblem}
import vql.quantifier.VagueQuantifier
import vql.result.{EvaluationOutput, VagueQueryResult}
import vql.sampling.{
  HDRConfig,
  HDRSampler,
  ProportionEstimate,
  ProportionEstimator,
  SampleSizeCalculator,
  SamplingParams,
}

object TypedSemantics:

  private type Env = Map[String, Value]

  /** A formula's truth value. `Unknown` holds the dispatcher refusal that left it undecided. */
  private enum Truth:
    case Known(value: Boolean)
    case Unknown(refusal: QueryError.EvaluationError)

  def evaluate(
    query: BoundQuery,
    model: RuntimeModel,
    answerTuple: Map[String, Value] = Map.empty,
    samplingParams: SamplingParams = SamplingParams.exact,
    hdrConfig: HDRConfig = HDRConfig.default,
  ): Either[QueryError, EvaluationOutput[Value]] =
    val quantifier = VagueQuantifier.fromQuantifier(query.quantifier)
    for
      baseEnv       <- validateAnswerTuple(query, answerTuple)
      rootDomain    <- model.elements
        .get(query.variable.sort)
        .toRight(
          QueryError.DomainNotFoundError(
            typeName = query.variable.sort,
            availableTypes = model.domains.keySet,
          )
        )
      rangeElements <- collectRangeElements(query, model, baseEnv, rootDomain)
      output        <- evaluateOverRange(
        query,
        model,
        baseEnv,
        rangeElements,
        quantifier,
        samplingParams,
        hdrConfig,
      )
    yield output
    end for

  end evaluate

  /**
   * Evaluate a bound formula with one free variable to its exact satisfying set over that
   * variable's sort (ADR-017 §6).
   *
   * Enumerates the variable's domain in its declared order and keeps each element for which the
   * formula holds with the variable bound to it. Exhaustive and deterministic; no sampling.
   */
  def satisfyingSet(
    formula: BoundFormula,
    variable: BoundVar,
    model: RuntimeModel,
  ): Either[QueryError, Set[Value]] =
    model.elements.get(variable.sort) match
      case None         =>
        Left(
          QueryError.DomainNotFoundError(
            typeName = variable.sort,
            availableTypes = model.domains.keySet,
          )
        )
      case Some(domain) =>
        domain.foldLeft[Either[QueryError, Set[Value]]](Right(Set.empty)) { (acc, candidate) =>
          for
            sat <- acc
            ok  <- holds(formula, Map(variable.name -> candidate), model)
          yield if ok then sat + candidate else sat
        }

  private def validateAnswerTuple(
    query: BoundQuery,
    answerTuple: Map[String, Value],
  ): Either[QueryError, Env] =
    query.answerVars.foldLeft[Either[QueryError, Env]](Right(answerTuple)) { (acc, boundVar) =>
      acc.flatMap { env =>
        env.get(boundVar.name) match
          case None                               =>
            Left(
              QueryError.ValidationError(
                ValidationProblem.MissingAnswerValue(VariableText.of(boundVar.name)),
                SourcePosition.Unknown,
              )
            )
          case Some(v) if v.sort == boundVar.sort => Right(env)
          case Some(v)                            =>
            Left(
              QueryError.ValidationError(
                ValidationProblem.AnswerSortMismatch(VariableText.of(boundVar.name), boundVar.sort, v.sort),
                SourcePosition.Unknown,
              )
            )
      }
    }

  private def collectRangeElements(
    query: BoundQuery,
    model: RuntimeModel,
    baseEnv: Env,
    rootDomain: Vector[Value],
  ): Either[QueryError, Vector[Value]] =
    rootDomain.foldLeft[Either[QueryError, Vector[Value]]](Right(Vector.empty)) { (acc, candidate) =>
      for
        accepted <- acc
        inRange  <- holds(query.range, baseEnv + (query.variable.name -> candidate), model)
      yield if inRange then accepted :+ candidate else accepted
    }

  private def evaluateOverRange(
    query: BoundQuery,
    model: RuntimeModel,
    baseEnv: Env,
    rangeElements: Vector[Value],
    quantifier: VagueQuantifier,
    samplingParams: SamplingParams,
    hdrConfig: HDRConfig,
  ): Either[QueryError, EvaluationOutput[Value]] =
    if rangeElements.isEmpty then
      Right(
        EvaluationOutput(
          result = emptyResult(quantifier),
          rangeElements = Set.empty,
          satisfyingElements = Set.empty,
        )
      )
    else
      val n       = SampleSizeCalculator.calculateSampleSize(rangeElements.size, samplingParams)
      val sampler = HDRSampler[Value](hdrConfig)
      val sample  = sampler.sample(rangeElements, n)
      for satisfying <- collectSatisfyingSample(query, model, baseEnv, sample)
      yield
        val estimate = ProportionEstimator.estimateFromCount(
          successes = satisfying.size,
          sampleSize = sample.size,
          params = samplingParams,
        )
        val result   = VagueQueryResult.fromEstimate(quantifier, estimate, rangeElements.size)
        EvaluationOutput(
          result = result,
          rangeElements = rangeElements.toSet,
          satisfyingElements = satisfying,
        )

  private def collectSatisfyingSample(
    query: BoundQuery,
    model: RuntimeModel,
    baseEnv: Env,
    sample: Set[Value],
  ): Either[QueryError, Set[Value]] =
    sample.foldLeft[Either[QueryError, Set[Value]]](Right(Set.empty)) { (acc, candidate) =>
      for
        sat <- acc
        ok  <- holds(query.scope, baseEnv + (query.variable.name -> candidate), model)
      yield if ok then sat + candidate else sat
    }

  /** The formula's truth value, failing with the refusal that left it unknown. */
  private def holds(formula: BoundFormula, env: Env, model: RuntimeModel): Either[QueryError, Boolean] =
    evalFormula(formula, env, model).flatMap {
      case Truth.Known(value)     => Right(value)
      case Truth.Unknown(refusal) => Left(refusal)
    }

  /** A dispatcher refusal becomes `Unknown`; any other failure stays a failure. */
  private def classify(result: Either[QueryError, Boolean]): Either[QueryError, Truth] =
    result match
      case Right(value) => Right(Truth.Known(value))
      case Left(
            refusal @ QueryError.EvaluationError(
              EvaluationFailure.PredicateRefused(_, _) | EvaluationFailure.FunctionRefused(_, _)
            )
          ) => Right(Truth.Unknown(refusal))
      case Left(defect) => Left(defect)

  /**
   * The formula's truth value under Kleene's strong three-valued logic (ADR-025). An operand that
   * decides the result alone stops evaluation; otherwise both operands are evaluated, so the result
   * does not depend on their order.
   */
  private def evalFormula(
    formula: BoundFormula,
    env: Env,
    model: RuntimeModel,
  ): Either[QueryError, Truth] =
    formula match
      case BoundFormula.True            => Right(Truth.Known(true))
      case BoundFormula.False           => Right(Truth.Known(false))
      case BoundFormula.Atom(a)         => classify(evalAtom(a, env, model))
      case BoundFormula.Not(p)          => evalFormula(p, env, model).map(negate)
      case BoundFormula.And(p, q)       =>
        evalFormula(p, env, model).flatMap {
          case Truth.Known(false) => Right(Truth.Known(false))
          case left               => evalFormula(q, env, model).map(conjoin(left, _))
        }
      case BoundFormula.Or(p, q)        =>
        evalFormula(p, env, model).flatMap {
          case Truth.Known(true) => Right(Truth.Known(true))
          case left              => evalFormula(q, env, model).map(disjoin(left, _))
        }
      case BoundFormula.Imp(p, q)       =>
        evalFormula(p, env, model).flatMap {
          case Truth.Known(false) => Right(Truth.Known(true)) // false antecedent → vacuously true
          case left               => evalFormula(q, env, model).map(disjoin(negate(left), _))
        }
      case BoundFormula.Iff(p, q)       =>
        for
          left  <- evalFormula(p, env, model)
          right <- evalFormula(q, env, model)
        yield (left, right) match
          case (Truth.Known(l), Truth.Known(r)) => Truth.Known(l == r)
          case (unknown: Truth.Unknown, _)      => unknown
          case (_, unknown: Truth.Unknown)      => unknown
      case BoundFormula.Forall(v, body) => quantify(body, v, env, model, decisive = false)
      case BoundFormula.Exists(v, body) => quantify(body, v, env, model, decisive = true)

  private def negate(truth: Truth): Truth =
    truth match
      case Truth.Known(value)     => Truth.Known(!value)
      case unknown: Truth.Unknown => unknown

  private def conjoin(left: Truth, right: Truth): Truth =
    (left, right) match
      case (Truth.Known(false), _) | (_, Truth.Known(false)) => Truth.Known(false)
      case (Truth.Known(true), _)                            => right
      case (unknown: Truth.Unknown, _)                       => unknown

  private def disjoin(left: Truth, right: Truth): Truth =
    (left, right) match
      case (Truth.Known(true), _) | (_, Truth.Known(true)) => Truth.Known(true)
      case (Truth.Known(false), _)                         => right
      case (unknown: Truth.Unknown, _)                     => unknown

  /**
   * `∃` when `decisive` is true and `∀` when it is false. The first binding whose body is
   * `Known(decisive)` decides. Otherwise the result is the first `Unknown` met, else
   * `Known(!decisive)`.
   */
  private def quantify(
    body: BoundFormula,
    variable: BoundVar,
    env: Env,
    model: RuntimeModel,
    decisive: Boolean,
  ): Either[QueryError, Truth] =
    model.elements.get(variable.sort) match
      case None         =>
        Left(
          QueryError.DomainNotFoundError(
            typeName = variable.sort,
            availableTypes = model.domains.keySet,
          )
        )
      case Some(domain) =>
        @scala.annotation.tailrec
        def walk(remaining: List[Value], firstUnknown: Option[Truth.Unknown]): Either[QueryError, Truth] =
          remaining match
            case Nil           => Right(firstUnknown.getOrElse(Truth.Known(!decisive)))
            case value :: rest =>
              evalFormula(body, env + (variable.name -> value), model) match
                case Right(Truth.Known(b)) if b == decisive => Right(Truth.Known(decisive))
                case Right(Truth.Known(_))                  => walk(rest, firstUnknown)
                case Right(unknown: Truth.Unknown)          => walk(rest, firstUnknown.orElse(Some(unknown)))
                case Left(defect)                           => Left(defect)
        walk(domain.toList, None)

  private def evalAtom(atom: BoundAtom, env: Env, model: RuntimeModel): Either[
    QueryError,
    Boolean,
  ] =
    for
      args  <- evalTerms(atom.args, env, model)
      value <- model.dispatcher
        .evalPredicate(atom.name, args)
        .left
        .map(msg => QueryError.EvaluationError(EvaluationFailure.PredicateRefused(atom.name, msg)))
    yield value

  private def evalTerms(terms: List[BoundTerm], env: Env, model: RuntimeModel): Either[
    QueryError,
    List[Value],
  ] =
    terms.foldLeft[Either[QueryError, List[Value]]](Right(Nil)) { (acc, term) =>
      for
        xs <- acc
        x  <- evalTerm(term, env, model)
      yield xs :+ x
    }

  private def evalTerm(term: BoundTerm, env: Env, model: RuntimeModel): Either[QueryError, Value] =
    term match
      case BoundTerm.VarRef(v) =>
        env
          .get(v.name)
          .toRight(
            QueryError.UnboundVariableError(VariableText.of(v.name), env.keySet)
          )
          .flatMap { value =>
            if value.sort == v.sort then Right(value)
            else
              Left(
                QueryError.EvaluationError(
                  EvaluationFailure.VariableSortMismatch(VariableText.of(v.name), v.sort, value.sort)
                )
              )
          }

      case BoundTerm.ConstRef(name, sort) =>
        // Named constant: the IR carries no payload, so the name itself is the
        // raw carrier, resolved by the runtime model.
        Right(Value(sort, name))

      case BoundTerm.LiteralRef(_, sort, value) =>
        // value is the parsed literal carrier (Any) produced by the sort's
        // literalValidator (ADR-015 §1, §4 / ADR-016). Dispatcher lambdas
        // recover the typed view via value.extract[A] (ADR-015 §2).
        Right(Value(sort, value))

      case BoundTerm.FnApp(name, args, sort) =>
        for
          argValues <- evalTerms(args, env, model)
          result    <- model.dispatcher
            .evalFunction(name, argValues)
            .left
            .map(msg => QueryError.EvaluationError(EvaluationFailure.FunctionRefused(name, msg)))
        yield Value(sort, result)

  private def emptyResult(quantifier: VagueQuantifier): VagueQueryResult =
    VagueQueryResult(
      satisfied = false,
      proportion = 0.0,
      confidenceInterval = (0.0, 1.0),
      quantifier = quantifier,
      domainSize = 0,
      sampleSize = 0,
      satisfyingCount = 0,
      estimate = ProportionEstimate.empty(),
    )

end TypedSemantics
