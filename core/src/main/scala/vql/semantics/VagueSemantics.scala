package vql.semantics

import logic.{FOL, Formula}
import vql.error.QueryError
import vql.logic.ParsedQuery
import vql.result.EvaluationOutput
import vql.sampling.{HDRConfig, SamplingParams}
import vql.typed.{
  BoundQuery,
  FolModel,
  QueryBinder,
  TypeCatalog,
  TypeCheckError,
  TypedSemantics,
  Value,
}

/**
 * Vague quantifier semantics evaluation — thin facade over the typed pipeline.
 *
 * A string-parsed [[ParsedQuery]] is bound against a [[TypeCatalog]] and evaluated against a
 * [[FolModel]] through the many-sorted typed backend:
 *
 *   1. `bindTyped` — type-check variable sorts against the catalog, producing a [[BoundQuery]].
 *   2. `evaluateTyped` — run the bound query against the model's dispatchers and domains via
 *      [[vql.typed.TypedSemantics]].
 *
 * `SamplingParams` controls whether scope evaluation is exact or sampled; range extraction is
 * always exact. The public API returns `Either[QueryError, A]`.
 *
 * `satisfyingSet` is a separate entry point: it evaluates a bare formula with one free variable to
 * its exact satisfying set over that variable's sort, with no quantifier and no sampling (ADR-017
 * §6).
 *
 * Paper reference: Section 3, Definition 2 See also: [[vql.typed.TypedSemantics]] for the canonical
 * typed evaluation path (ADR-001)
 */
object VagueSemantics:

  /**
   * Bind a parsed query into the typed IL (type-checks variable sorts against the catalog).
   *
   * Canonical bind entrypoint; runtime evaluation follows in `evaluateTyped`.
   */
  def bindTyped(
    query: ParsedQuery,
    catalog: TypeCatalog,
  ): Either[QueryError, BoundQuery] =
    QueryBinder
      .bind(query, catalog)
      .left
      .map { errors =>
        val unknowns = errors.collect { case TypeCheckError.UnknownConstantOrLiteral(name) => name }
        val others   = errors.filterNot {
          case _: TypeCheckError.UnknownConstantOrLiteral => true; case _ => false
        }
        if others.isEmpty && unknowns.nonEmpty then
          QueryError.UnknownConstantOrLiteralError(unknowns.head)
        else QueryError.BindError(errors = renderTypeErrors(errors))
      }

  private def renderTypeErrors(errors: List[TypeCheckError]): List[String] =
    errors.map {
      case TypeCheckError.UnknownPredicate(name)                  => s"unknown predicate: $name"
      case TypeCheckError.UnknownFunction(name)                   => s"unknown function: $name"
      case TypeCheckError.ArityMismatch(symbol, expected, actual) =>
        s"arity mismatch for '$symbol': expected $expected, actual $actual"
      case TypeCheckError.UnknownConstantOrLiteral(name) => s"unknown constant or literal: $name"
      case TypeCheckError.TypeMismatch(expected, actual, context) =>
        s"type mismatch in $context: expected ${expected.value}, actual ${actual.value}"
      case TypeCheckError.UnboundAnswerVar(name) => s"unbound answer variable: $name"
      case TypeCheckError.UnconstrainedVar(name) => s"unconstrained quantifier variable: $name"
      case TypeCheckError.ConflictingTypes(name, left, right)      =>
        s"conflicting inferred types for '$name': ${left.value} vs ${right.value}"
      case TypeCheckError.TypeNotQuantifiable(name)                =>
        s"type '$name' is not a domain type and cannot be quantified over"
      case TypeCheckError.UnparseableConstant(_, sort, sourceText) =>
        s"cannot parse '$sourceText' as ${sort.value}"
      case TypeCheckError.UnexpectedFreeVar(name)                  =>
        s"unexpected free variable '$name': a satisfying-set formula may only mention its one variable"
    }

  /**
   * Evaluate a parsed query through the typed pipeline using a pre-validated [[FolModel]].
   *
   * Model validation (dispatcher coverage + domain registration) is guaranteed by [[FolModel]]
   * construction — this overload skips per-query re-validation. Canonical flow: ParsedQuery →
   * bindTyped → TypedSemantics.evaluate
   */
  def evaluateTyped(
    query: ParsedQuery,
    folModel: FolModel,
    answerTuple: Map[String, Value] = Map.empty,
    samplingParams: SamplingParams = SamplingParams.exact,
    hdrConfig: HDRConfig = HDRConfig.default,
  ): Either[QueryError, EvaluationOutput[Value]] =
    for
      bound  <- bindTyped(query, folModel.catalog)
      output <- TypedSemantics.evaluate(
        query = bound,
        model = folModel.model,
        answerTuple = answerTuple,
        samplingParams = samplingParams,
        hdrConfig = hdrConfig,
      )
    yield output

  /**
   * Evaluate a bare formula with one free variable to its exact satisfying set over that variable's
   * sort (ADR-017 §6).
   *
   * The formula is bound from an empty environment; its free variables must be exactly `variable`,
   * whose inferred sort must be a domain type. Enumeration over that domain is exhaustive and
   * deterministic — there are no sampling parameters. Input is a pre-parsed [[logic.Formula]];
   * callers with query text parse it via `FOLParser.parse` and map the foundation parse error at
   * their own boundary.
   */
  def satisfyingSet(
    formula: Formula[FOL],
    variable: String,
    folModel: FolModel,
  ): Either[QueryError, Set[Value]] =
    for
      bound <- QueryBinder
        .bindSatisfyingFormula(formula, variable, folModel.catalog)
        .left
        .map(errors => QueryError.BindError(errors = renderTypeErrors(errors)))
      (boundFormula, boundVar) = bound
      result <- TypedSemantics.satisfyingSet(boundFormula, boundVar, folModel.model)
    yield result

end VagueSemantics
