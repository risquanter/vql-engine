package parser

import munit.FunSuite
import printer.FOLPrinter
import vql.fragment.{Fragment, FragmentCheck}
import vql.parser.VagueQueryParser
import vql.sampling.SamplingParams
import vql.semantics.VagueSemantics
import vql.typed.{FolModel, FunctionSig, OrderedDomain, PredicateSig, RuntimeDispatcher, RuntimeModel, SymbolName, TypeCatalog, TypeId, Value}
import vql.typed.TypeDecl.DomainType

/** Runs a query at both nesting limits through the whole pipeline on a thread whose stack is 1 MiB. */
class NestingBoundStackSpec extends FunSuite:

  /** The stack size the nesting limits are measured against (ADR-024). */
  private val stackBytes: Long = 1L << 20

  private val asset = TypeId("Asset")

  private val model =
    val catalog    = TypeCatalog.unsafe(
      types = Set(DomainType(asset)),
      functions = Map(SymbolName("f") -> FunctionSig(List(asset), asset)),
      predicates = Map(SymbolName("p") -> PredicateSig(List(asset))),
      literalValidators = Map.empty,
    )
    val dispatcher = new RuntimeDispatcher:
      override def evalFunction(name: SymbolName, args: List[Value]): Either[String, Any] =
        (name.value, args) match
          case ("f", List(a)) => Right(a.raw)
          case (other, _)     => Left(s"No function implementation for '$other'")
      override def evalPredicate(name: SymbolName, args: List[Value]): Either[String, Boolean] =
        (name.value, args) match
          case ("p", List(_)) => Right(true)
          case (other, _)     => Left(s"No predicate implementation for '$other'")
      override def functionSymbols: Set[SymbolName]  = Set(SymbolName("f"))
      override def predicateSymbols: Set[SymbolName] = Set(SymbolName("p"))
    FolModel(catalog, RuntimeModel(domains = Map(asset -> OrderedDomain.of(List("a1"))), dispatcher = dispatcher))
      .fold(e => fail(s"FolModel construction failed: $e"), identity)

  end model

  /** 253 negations and the header's three operators make 256; the query's `(`, `p(` and 62 `f(`s make 64. */
  private val scope: String = "~ " * 253 + "p(" + "f(" * 62 + "x" + ")" * 62 + ")"

  /** `body`'s exception, if it throws one, when run on a thread with a stack of `stackBytes`. */
  private def thrownOnSmallStack(body: => Unit): Option[Throwable] =
    var thrown: Option[Throwable] = None
    val thread                    = new Thread(
      null,
      () =>
        try body
        catch case e: Throwable => thrown = Some(e),
      "nesting-bound",
      stackBytes,
    )
    thread.start()
    thread.join()
    thrown

  test("a query at both nesting limits runs through parse, evaluation, the fragment check and the printer on 1 MiB") {
    val thrown = thrownOnSmallStack {
      val query = VagueQueryParser.parse(s"Q[>=]^{1/2} x (p(x), $scope)").fold(e => throw new AssertionError(e.formatted), identity)
      VagueSemantics.evaluateTyped(query, model, samplingParams = SamplingParams.exact).fold(e => throw new AssertionError(e.formatted), identity)
      val formula = FOLParser.parse(scope).fold(e => throw new AssertionError(e.message), identity)
      val targeting = Fragment.Targeting(Set(SymbolName("f")), Set(SymbolName("q")))
      FragmentCheck.check(formula, targeting).fold(v => throw new AssertionError(v.toString), identity)
      FragmentCheck.violations(formula, targeting) match
        case Nil        => ()
        case violations => throw new AssertionError(violations.toString)
      FragmentCheck.literalArguments(formula, SymbolName("f"))
      FOLPrinter.print(formula)
      ()
    }
    assertEquals(thrown, None)
  }

end NestingBoundStackSpec
