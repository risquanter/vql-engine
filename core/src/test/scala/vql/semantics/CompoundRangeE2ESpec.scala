package vql.semantics

import munit.FunSuite
import vql.parser.VagueQueryParser
import vql.sampling.SamplingParams
import vql.typed.{
  FolModel,
  PredicateSig,
  RuntimeDispatcher,
  RuntimeModel,
  SymbolName,
  TypeCatalog,
  TypeId,
  Value,
}
import vql.typed.TypeDecl.DomainType

/**
 * End-to-end tests for formula ranges: parse a compound-range query string and evaluate it,
 * reproducing the IR-level results through the parser (AC-1), and pinning that range extraction
 * stays exhaustive under scope sampling (AC-6, ADR-017 §2).
 */
class CompoundRangeE2ESpec extends FunSuite:

  private val item = TypeId("Item")

  private val catalog = TypeCatalog.unsafe(
    types = Set(DomainType(item)),
    predicates = Map(
      SymbolName("p")   -> PredicateSig(List(item)),
      SymbolName("q")   -> PredicateSig(List(item)),
      SymbolName("sat") -> PredicateSig(List(item)),
    ),
  )

  /** Dispatcher over the `Item` sort with predicates `p`, `q`, `sat`. */
  private def dispatcherOf(
    p: String => Boolean,
    q: String => Boolean,
    sat: String => Boolean,
  ): RuntimeDispatcher =
    new RuntimeDispatcher:
      override def evalFunction(name: SymbolName, args: List[Value]): Either[String, Any]      =
        Left("no functions")
      override def evalPredicate(name: SymbolName, args: List[Value]): Either[String, Boolean] =
        val raw = args.head.raw.toString
        name.value match
          case "p"   => Right(p(raw))
          case "q"   => Right(q(raw))
          case "sat" => Right(sat(raw))
          case other => Left(s"no predicate: $other")
      override def functionSymbols: Set[SymbolName] = Set.empty
      override def predicateSymbols: Set[SymbolName]                                           =
        Set(SymbolName("p"), SymbolName("q"), SymbolName("sat"))

  private def evaluate(queryStr: String, model: RuntimeModel) =
    val fm = FolModel(catalog, model).fold(e => fail(s"FolModel construction failed: $e"), identity)
    val query = VagueQueryParser
      .parse(queryStr)
      .fold(e => fail(s"parse failed: ${e.formatted}"), identity)
    VagueSemantics
      .evaluateTyped(query, fm, samplingParams = SamplingParams.exact)
      .fold(e => fail(s"evaluation failed: $e"), identity)

  test("parsed conjunction range extracts the intersection population (AC-1, AC-2)"):
    val a      = Value(item, "a"); val b = Value(item, "b")
    val c      = Value(item, "c"); val d = Value(item, "d")
    // p = {a,b}, q = {b,c} → p /\ q = {b}
    val model  = RuntimeModel(
      domains = Map(item -> Set(a, b, c, d)),
      dispatcher = dispatcherOf(
        p = r => r == "a" || r == "b",
        q = r => r == "b" || r == "c",
        sat = _ => true,
      ),
    )
    val output = evaluate("Q[~]^{1/2} x (p(x) /\\ q(x), sat(x))", model)
    assertEquals(output.rangeElements, Set(b))
    assertEquals(output.satisfyingElements, Set(b))
    // Denominator is the compound population |D_R| = 1, not the sort domain (4).
    assertEquals(output.result.domainSize, 1)

  test("parsed negation range is the closed-world complement (AC-1)"):
    val a      = Value(item, "a"); val b = Value(item, "b")
    val c      = Value(item, "c"); val d = Value(item, "d")
    // p = {a,b} → ~p = {c,d}
    val model  = RuntimeModel(
      domains = Map(item -> Set(a, b, c, d)),
      dispatcher = dispatcherOf(
        p = r => r == "a" || r == "b",
        q = _ => false,
        sat = _ => true,
      ),
    )
    val output = evaluate("Q[~]^{1/2} x (~p(x), sat(x))", model)
    assertEquals(output.rangeElements, Set(c, d))

  test("range extraction stays exhaustive under scope sampling (AC-6)"):
    // 100-element domain; p true on all, q true on even-indexed → range p /\ q = 50 elements.
    // A strict (non-exact) sampling parameter samples the SCOPE only; the range
    // population is still the full 50-element compound set.
    val items  = (0 until 100).map(i => Value(item, i.toString)).toSet
    val model  = RuntimeModel(
      domains = Map(item -> items),
      dispatcher = dispatcherOf(
        p = _ => true,
        q = r => r.toInt % 2 == 0,
        sat = _ => true,
      ),
    )
    val fm = FolModel(catalog, model).fold(e => fail(s"FolModel construction failed: $e"), identity)
    val query  = VagueQueryParser
      .parse("Q[~]^{1/2} x (p(x) /\\ q(x), sat(x))")
      .fold(e => fail(s"parse failed: ${e.formatted}"), identity)
    val output = VagueSemantics
      .evaluateTyped(query, fm, samplingParams = SamplingParams.fast)
      .fold(e => fail(s"evaluation failed: $e"), identity)
    // Range extraction is exhaustive: all 50 even-indexed items.
    assertEquals(output.rangeElements.size, 50)
    // Scope sampling actually reduced the sample below the population.
    assert(
      output.result.sampleSize < 50,
      s"expected a strict sample, got sampleSize=${output.result.sampleSize}",
    )

end CompoundRangeE2ESpec
