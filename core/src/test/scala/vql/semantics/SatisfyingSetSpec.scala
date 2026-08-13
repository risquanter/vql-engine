package vql.semantics

import vql.error.QueryError
import vql.typed.TypeDecl.*
import vql.typed.{FolModel, PredicateSig, QueryBinder, RuntimeDispatcher, RuntimeModel, SymbolName, TypeCatalog, TypeCheckError, TypeId, Value}
import logic.{FOL, Formula, Term}
import munit.FunSuite

/** Satisfying-set entry point (ADR-017 §6): evaluate a bare formula with one
  * free variable to its exact satisfying set over that variable's sort. No
  * quantifier, no sampling — enumeration is exhaustive and deterministic.
  */
class SatisfyingSetSpec extends FunSuite:

  private val item = TypeId("Item")

  private val catalog = TypeCatalog.unsafe(
    types = Set(DomainType(item)),
    predicates = Map(
      SymbolName("p") -> PredicateSig(List(item)),
      SymbolName("q") -> PredicateSig(List(item)),
      SymbolName("r") -> PredicateSig(List(item, item))
    )
  )

  private val a = Value(item, "a")
  private val b = Value(item, "b")
  private val c = Value(item, "c")
  private val d = Value(item, "d")

  // p = {a,b}, q = {b,c}, r = {(a,b), (c,d)}
  private val dispatcher = new RuntimeDispatcher:
    override def evalFunction(name: SymbolName, args: List[Value]): Either[String, Any] =
      Left("no functions")
    override def evalPredicate(name: SymbolName, args: List[Value]): Either[String, Boolean] =
      (name.value, args.map(_.raw.toString)) match
        case ("p", List(x))    => Right(x == "a" || x == "b")
        case ("q", List(x))    => Right(x == "b" || x == "c")
        case ("r", List(x, y)) => Right((x == "a" && y == "b") || (x == "c" && y == "d"))
        case (other, _)        => Left(s"no predicate: $other")
    override def functionSymbols: Set[SymbolName] = Set.empty
    override def predicateSymbols: Set[SymbolName] =
      Set(SymbolName("p"), SymbolName("q"), SymbolName("r"))

  private val model = RuntimeModel(domains = Map(item -> Set(a, b, c, d)), dispatcher = dispatcher)
  private val folModel = FolModel(catalog, model).fold(e => fail(s"FolModel construction failed: $e"), identity)

  private def atom(name: String, terms: Term*): Formula[FOL] =
    Formula.Atom(FOL(name, terms.toList))

  private def satisfyingSet(f: Formula[FOL], v: String = "x"): Set[Value] =
    VagueSemantics.satisfyingSet(f, v, folModel).fold(e => fail(s"satisfyingSet failed: $e"), identity)

  // ==================== Semantics (AC-5) ====================

  test("negation is the closed-world complement over the active domain (AC-5)"):
    // ~p(x) with p = {a,b} → {c,d}
    assertEquals(satisfyingSet(Formula.Not(atom("p", Term.Var("x")))), Set(c, d))

  test("conjunction is the intersection (AC-5)"):
    // p(x) /\ q(x) with p = {a,b}, q = {b,c} → {b}
    assertEquals(satisfyingSet(Formula.And(atom("p", Term.Var("x")), atom("q", Term.Var("x")))), Set(b))

  test("existential quantifies over an inner variable (AC-5)"):
    // { x | ∃a. r(x, a) } with r = {(a,b),(c,d)} → {a, c}
    val f = Formula.Exists("a", atom("r", Term.Var("x"), Term.Var("a")))
    assertEquals(satisfyingSet(f), Set(a, c))

  test("an unsatisfiable formula yields the empty set (AC-5)"):
    val f = Formula.And(atom("p", Term.Var("x")), Formula.Not(atom("p", Term.Var("x"))))
    assertEquals(satisfyingSet(f), Set.empty[Value])

  // ==================== Exactness and determinism ====================

  test("exactness: result equals the brute-force domain filter"):
    val f = atom("p", Term.Var("x"))
    val brute = Set(a, b, c, d).filter(v => v == a || v == b)
    assertEquals(satisfyingSet(f), brute)

  test("determinism: two evaluations produce equal sets"):
    val f = Formula.Or(atom("p", Term.Var("x")), atom("q", Term.Var("x")))
    assertEquals(satisfyingSet(f), satisfyingSet(f))
    assertEquals(satisfyingSet(f), Set(a, b, c))

  // ==================== Free-variable validation (ADR-017 §6) ====================

  test("rejects a formula with an extra free variable (UnexpectedFreeVar)"):
    // r(x, y): y is a second free variable, not the satisfying-set variable.
    val f = atom("r", Term.Var("x"), Term.Var("y"))
    QueryBinder.bindSatisfyingFormula(f, "x", catalog) match
      case Left(errs) => assert(errs.contains(TypeCheckError.UnexpectedFreeVar("y")), s"got $errs")
      case Right(_)   => fail("expected Left for extra free variable")

  test("rejects a formula that does not contain the variable (UnconstrainedVar)"):
    val f = atom("p", Term.Var("x"))
    QueryBinder.bindSatisfyingFormula(f, "z", catalog) match
      case Left(errs) => assert(errs.contains(TypeCheckError.UnconstrainedVar("z")), s"got $errs")
      case Right(_)   => fail("expected Left when the variable is absent")

  test("rejects a variable whose inferred sort is a value type (TypeNotQuantifiable, ADR-014)"):
    val loss = TypeId("Loss")
    val valueCatalog = TypeCatalog.unsafe(
      types = Set(ValueType(loss)),
      predicates = Map(SymbolName("gt_loss") -> PredicateSig(List(loss, loss)))
    )
    val f = atom("gt_loss", Term.Var("l"), Term.Var("l"))
    QueryBinder.bindSatisfyingFormula(f, "l", valueCatalog) match
      case Left(errs) => assert(errs.contains(TypeCheckError.TypeNotQuantifiable("Loss")), s"got $errs")
      case Right(_)   => fail("expected Left for a value-type variable")

  test("facade surfaces an unexpected free variable as a BindError"):
    val f = atom("r", Term.Var("x"), Term.Var("y"))
    VagueSemantics.satisfyingSet(f, "x", folModel) match
      case Left(QueryError.BindError(errors)) => assert(errors.exists(_.contains("y")), s"got $errors")
      case other                              => fail(s"expected BindError, got $other")
