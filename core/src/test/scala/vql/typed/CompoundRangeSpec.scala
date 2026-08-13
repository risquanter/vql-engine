package vql.typed

import vql.quantifier.{Quantifier, VagueQuantifier}
import vql.result.{EvaluationOutput, VagueQueryResult}
import vql.sampling.{ProportionEstimator, SamplingParams}
import munit.FunSuite

/** IR-level tests for formula ranges (ADR-017 §§1–3).
  *
  * Constructs `BoundQuery` values programmatically — no parser or `ParsedQuery`
  * involvement — and asserts on the extracted range set `D_R`.
  *
  * Fixture: sort `Item` with active domain `{i1,i2,i3,i4}`, sort `Tag` with
  * `{t1,t2}`. `p` holds on `{i1,i2}`, `q` holds on `{i2,i3}`, `r` holds on the
  * pairs `{(i1,t1),(i3,t2)}`.
  */
class CompoundRangeSpec extends FunSuite:

  private val item = TypeId("Item")
  private val tag  = TypeId("Tag")

  private val i1 = Value(item, "i1")
  private val i2 = Value(item, "i2")
  private val i3 = Value(item, "i3")
  private val i4 = Value(item, "i4")
  private val t1 = Value(tag, "t1")
  private val t2 = Value(tag, "t2")

  private val pSet   = Set("i1", "i2")
  private val qSet   = Set("i2", "i3")
  private val rPairs = Set(("i1", "t1"), ("i3", "t2"))

  private val dispatcher = new RuntimeDispatcher:
    override def evalFunction(name: SymbolName, args: List[Value]): Either[String, Any] =
      Left(s"no function: ${name.value}")
    override def evalPredicate(name: SymbolName, args: List[Value]): Either[String, Boolean] =
      name.value match
        case "p"   => Right(pSet.contains(args.head.raw.toString))
        case "q"   => Right(qSet.contains(args.head.raw.toString))
        case "r"   => Right(rPairs.contains((args.head.raw.toString, args(1).raw.toString)))
        case other => Left(s"unknown predicate: $other")
    override def functionSymbols: Set[SymbolName] = Set.empty
    override def predicateSymbols: Set[SymbolName] =
      Set(SymbolName("p"), SymbolName("q"), SymbolName("r"))

  private val model = RuntimeModel(
    domains    = Map(item -> Set(i1, i2, i3, i4), tag -> Set(t1, t2)),
    dispatcher = dispatcher
  )

  private val x = BoundVar("x", item)

  private def atom(pred: String, terms: BoundTerm*): BoundFormula =
    BoundFormula.Atom(BoundAtom(SymbolName(pred), terms.toList))

  private val px = atom("p", BoundTerm.VarRef(x))
  private val qx = atom("q", BoundTerm.VarRef(x))

  /** A query whose scope is `True`, so `satisfyingElements == rangeElements`. */
  private def rangeQuery(range: BoundFormula): BoundQuery =
    BoundQuery(
      quantifier = Quantifier.About(1, 2, 0.01),
      variable   = x,
      range      = range,
      scope      = BoundFormula.True
    )

  private def rangeElementsOf(range: BoundFormula): Set[Value] =
    TypedSemantics
      .evaluate(rangeQuery(range), model, samplingParams = SamplingParams.exact)
      .fold(e => fail(s"evaluate failed: $e"), _.rangeElements)

  test("AC-1 conjunction range extracts the intersection P ∩ Q"):
    assertEquals(rangeElementsOf(BoundFormula.And(px, qx)), Set(i2))

  test("AC-1 disjunction range extracts the union P ∪ Q"):
    assertEquals(rangeElementsOf(BoundFormula.Or(px, qx)), Set(i1, i2, i3))

  test("AC-1 negation range is the closed-world complement D \\ P"):
    assertEquals(rangeElementsOf(BoundFormula.Not(px)), Set(i3, i4))

  test("AC-1 existential range extracts { d | ∃a. r(d,a) }"):
    val a = BoundVar("a", tag)
    val range = BoundFormula.Exists(a, atom("r", BoundTerm.VarRef(x), BoundTerm.VarRef(a)))
    assertEquals(rangeElementsOf(range), Set(i1, i3))

  test("AC-1 De Morgan: Not(And(p,q)) equals Or(Not p, Not q) as range sets"):
    val lhs = rangeElementsOf(BoundFormula.Not(BoundFormula.And(px, qx)))
    val rhs = rangeElementsOf(BoundFormula.Or(BoundFormula.Not(px), BoundFormula.Not(qx)))
    assertEquals(lhs, rhs)
    assertEquals(lhs, Set(i1, i3, i4))

  test("AC-2 denominator is the compound population, not the sort domain"):
    // range P ∩ Q = {i2}; scope True → all range elements satisfy
    val output = TypedSemantics
      .evaluate(rangeQuery(BoundFormula.And(px, qx)), model, samplingParams = SamplingParams.exact)
      .fold(e => fail(s"evaluate failed: $e"), identity)
    assertEquals(output.rangeElements, Set(i2))
    assertEquals(output.result.domainSize, 1) // |D_R|, not |Item| = 4
    assertEquals(output.satisfyingElements, Set(i2))

  test("AC-3 regression: single-atom range (BoundFormula.Atom) yields the expected full EvaluationOutput"):
    // range p(x) = {i1,i2}; scope q(x) satisfied on {i2}
    val boundQuery = BoundQuery(
      quantifier = Quantifier.About(1, 2, 0.01),
      variable   = x,
      range      = px, // the prior single-atom shape, now wrapped as BoundFormula.Atom
      scope      = qx
    )
    val output = TypedSemantics
      .evaluate(boundQuery, model, samplingParams = SamplingParams.exact)
      .fold(e => fail(s"evaluate failed: $e"), identity)

    // Full-output pin (AC-3). The estimate/CI are delegated to the same
    // estimator the production path uses, so this asserts every EvaluationOutput
    // field yet stays robust to an intended estimator change: it guards the
    // single-atom range/scope extraction (range = {i1,i2}, satisfying = {i2},
    // so 1 of 2), which the BoundFormula.Atom delegation must keep identical.
    val expected = EvaluationOutput(
      result = VagueQueryResult.fromEstimate(
        VagueQuantifier.fromQuantifier(Quantifier.About(1, 2, 0.01)),
        ProportionEstimator.estimateFromCount(successes = 1, sampleSize = 2, params = SamplingParams.exact),
        domainSize = 2
      ),
      rangeElements      = Set(i1, i2),
      satisfyingElements = Set(i2)
    )
    assertEquals(output, expected)
