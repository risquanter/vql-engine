package vql.typed

import lexer.VariableText
import munit.FunSuite
import vql.error.QueryError
import vql.error.QueryError.EvaluationFailure
import vql.quantifier.Quantifier
import vql.sampling.SamplingParams

/**
 * Evaluation of dispatcher refusals as unknown truth values (ADR-025). The model: `r(a1)` is true and
 * `r(a2)` false; `q(a1)` is true and `q(a2)` refused; `s` is true; `f(a1)` is `a1` and `f(a2)` refused.
 */
class KleeneEvaluationSpec extends FunSuite:

  private val asset = TypeId("Asset")
  private val other = TypeId("Other")
  private val x     = BoundVar("x", asset)
  private val y     = BoundVar("y", asset)

  private val r = SymbolName("r")
  private val q = SymbolName("q")
  private val s = SymbolName("s")
  private val t = SymbolName("t")
  private val f = SymbolName("f")

  private val qRefusal = "q is unknown for a2"

  private val dispatcher = MapDispatcher(
    predicates = Map(
      r -> (args => args.head.extract[String].map(_ == "a1")),
      q -> (args =>
        args.head.extract[String].flatMap {
          case "a1" => Right(true)
          case _    => Left(qRefusal)
        }
      ),
      s -> (_ => Right(true)),
      t -> (_ => Right(true)),
    ),
    functions = Map(
      f -> (args =>
        args.head.extract[String].flatMap {
          case "a1" => Right("a1")
          case _    => Left("f is unknown for a2")
        }
      )
    ),
  )

  private val model = RuntimeModel(domains = Map(asset -> OrderedDomain.of(List("a2", "a1"))), dispatcher = dispatcher)

  private def atom(name: SymbolName, variable: BoundVar): BoundFormula =
    BoundFormula.Atom(BoundAtom(name, List(BoundTerm.VarRef(variable))))

  /** The elements satisfying `scope` over the whole domain, or the evaluation's failure. */
  private def satisfying(scope: BoundFormula): Either[QueryError, Set[String]] =
    val query = BoundQuery(Quantifier.mkAbout(1, 2, 0.01), x, BoundFormula.True, scope)
    TypedSemantics
      .evaluate(query, model, samplingParams = SamplingParams.exact)
      .map(_.satisfyingElements.flatMap(_.extract[String].toOption))

  private val qRefused: Either[QueryError, Set[String]] =
    Left(QueryError.EvaluationError(EvaluationFailure.PredicateRefused(q, qRefusal)))

  test("a refusal that cannot change a conjunction's result leaves it false, in either operand order") {
    assertEquals(satisfying(BoundFormula.And(atom(r, x), atom(q, x))), Right(Set("a1")))
    assertEquals(satisfying(BoundFormula.And(atom(q, x), atom(r, x))), Right(Set("a1")))
  }

  test("a refusal that could change a disjunction's result fails it") {
    assertEquals(satisfying(BoundFormula.Or(atom(r, x), atom(q, x))), qRefused)
  }

  test("conjunction and disjunction give the same outcome in either operand order") {
    val operands = List(atom(r, x), atom(q, x), BoundFormula.Not(atom(r, x)), BoundFormula.Not(atom(q, x)))
    def outcome(scope: BoundFormula): Either[Unit, Set[String]] = satisfying(scope).left.map(_ => ())
    for
      left  <- operands
      right <- operands
    do
      assertEquals(outcome(BoundFormula.And(left, right)), outcome(BoundFormula.And(right, left)))
      assertEquals(outcome(BoundFormula.Or(left, right)), outcome(BoundFormula.Or(right, left)))
  }

  test("an implication with a false antecedent holds whatever its consequent") {
    assertEquals(satisfying(BoundFormula.Imp(atom(r, x), atom(q, x))), Right(Set("a1", "a2")))
  }

  test("a biconditional with an unknown side is unknown") {
    assertEquals(satisfying(BoundFormula.Iff(atom(r, x), atom(q, x))), qRefused)
  }

  test("a quantifier is decided by any deciding binding, and is otherwise unknown") {
    assertEquals(satisfying(BoundFormula.Exists(y, atom(q, y))), Right(Set("a1", "a2")))
    assertEquals(satisfying(BoundFormula.Forall(y, atom(q, y))), qRefused)
    assertEquals(satisfying(BoundFormula.Forall(y, BoundFormula.Not(atom(q, y)))), Right(Set.empty[String]))
  }

  test("a function refusal makes its atom unknown") {
    val sOfF = BoundFormula.Atom(BoundAtom(s, List(BoundTerm.FnApp(f, List(BoundTerm.VarRef(x)), asset))))
    assertEquals(satisfying(BoundFormula.And(sOfF, atom(r, x))), Right(Set("a1")))
  }

  test("a failure that is not a dispatcher refusal fails at once, even where another operand decides") {
    val mismatched = atom(t, BoundVar("x", other))
    assertEquals(
      satisfying(BoundFormula.Or(mismatched, BoundFormula.True)),
      Left(QueryError.EvaluationError(EvaluationFailure.VariableSortMismatch(VariableText.of("x"), other, asset))),
    )
  }

end KleeneEvaluationSpec
