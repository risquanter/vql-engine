package vql.fragment

import logic.{FOL, Formula, Term}
import parser.FOLParser
import munit.FunSuite

/** [[FragmentCheck]] decides structural membership of a parsed `Formula[FOL]`
  * in a [[Fragment]], returning the first violated rule (ADR-018). Unit tests
  * work over hand-built trees; the end-to-end section drives the public entry
  * path `FOLParser.parse` → `FragmentCheck.check`.
  */
class FragmentCheckSpec extends FunSuite:

  private def atom(name: String, terms: Term*): Formula[FOL] =
    Formula.Atom(FOL(name, terms.toList))

  import Term.{Var, Const, Fn}

  // ==================== Targeting: admit ====================

  test("targeting admits a bare atom over variables"):
    assertEquals(FragmentCheck.check(atom("p", Var("x")), Fragment.Targeting), Right(()))

  test("targeting admits inline literals (Term.Const) and variables"):
    // p(x, 42): the literal is a Const, not a function application
    assertEquals(FragmentCheck.check(atom("p", Var("x"), Const("42")), Fragment.Targeting), Right(()))

  test("targeting admits connectives over admissible atoms"):
    val f = Formula.And(atom("p", Var("x")), Formula.Not(atom("q", Var("y"))))
    assertEquals(FragmentCheck.check(f, Fragment.Targeting), Right(()))

  // ==================== Targeting: reject ====================

  test("targeting rejects a universal quantifier"):
    val f = Formula.Forall("x", atom("p", Var("x")))
    assertEquals(FragmentCheck.check(f, Fragment.Targeting), Left(FragmentViolation.QuantifierNotAllowed))

  test("targeting rejects an existential quantifier"):
    val f = Formula.Exists("x", atom("p", Var("x")))
    assertEquals(FragmentCheck.check(f, Fragment.Targeting), Left(FragmentViolation.QuantifierNotAllowed))

  test("targeting rejects a function-application term, naming the function"):
    val f = atom("p", Fn("f", List(Var("x"))))
    assertEquals(
      FragmentCheck.check(f, Fragment.Targeting),
      Left(FragmentViolation.FunctionApplicationNotAllowed("f"))
    )

  test("targeting rejects an arithmetic operator term (it is a Term.Fn)"):
    // gt(x + y, z): `+` is a function application
    val f = atom("gt", Fn("+", List(Var("x"), Var("y"))), Var("z"))
    assertEquals(
      FragmentCheck.check(f, Fragment.Targeting),
      Left(FragmentViolation.FunctionApplicationNotAllowed("+"))
    )

  test("targeting reports the outermost function of a nested application"):
    val f = atom("p", Fn("f", List(Fn("g", List(Var("x"))))))
    assertEquals(
      FragmentCheck.check(f, Fragment.Targeting),
      Left(FragmentViolation.FunctionApplicationNotAllowed("f"))
    )

  // ==================== Targeting: first-violation order (pre-order, left-to-right) ====================

  test("the left operand's violation is reported before the right operand's"):
    // left has a Fn, right has a quantifier: the Fn (left) wins
    val f = Formula.And(atom("p", Fn("f", List(Var("x")))), Formula.Exists("y", atom("q", Var("y"))))
    assertEquals(
      FragmentCheck.check(f, Fragment.Targeting),
      Left(FragmentViolation.FunctionApplicationNotAllowed("f"))
    )

  test("a right-side quantifier is reported when the left operand is admissible"):
    val f = Formula.And(atom("p", Var("x")), Formula.Exists("y", atom("q", Var("y"))))
    assertEquals(FragmentCheck.check(f, Fragment.Targeting), Left(FragmentViolation.QuantifierNotAllowed))

  test("a quantifier node is reported before a function application inside its body"):
    val f = Formula.Forall("x", atom("p", Fn("f", List(Var("x")))))
    assertEquals(FragmentCheck.check(f, Fragment.Targeting), Left(FragmentViolation.QuantifierNotAllowed))

  // ==================== Screening: depth ====================

  test("Screening(0) admits a quantifier-free formula"):
    assertEquals(FragmentCheck.check(atom("p", Var("x")), Fragment.Screening(0)), Right(()))

  test("Screening(0) rejects any quantifier, identical to targeting's quantifier rule"):
    val f = Formula.Forall("x", atom("p", Var("x")))
    assertEquals(
      FragmentCheck.check(f, Fragment.Screening(0)),
      Left(FragmentViolation.QuantifierDepthExceeded(0, 1))
    )

  test("Screening(1) admits one level of nesting"):
    val f = Formula.Forall("x", atom("p", Var("x")))
    assertEquals(FragmentCheck.check(f, Fragment.Screening(1)), Right(()))

  test("Screening(1) rejects two nested quantifiers, reporting the actual depth"):
    val f = Formula.Exists("x", Formula.Exists("y", atom("r", Var("x"), Var("y"))))
    assertEquals(
      FragmentCheck.check(f, Fragment.Screening(1)),
      Left(FragmentViolation.QuantifierDepthExceeded(1, 2))
    )

  test("Screening(2) admits two nested quantifiers"):
    val f = Formula.Exists("x", Formula.Exists("y", atom("r", Var("x"), Var("y"))))
    assertEquals(FragmentCheck.check(f, Fragment.Screening(2)), Right(()))

  test("side-by-side quantifiers do not add: only nesting counts"):
    // (∃x. p(x)) /\ (∃y. q(y)): each branch nests one deep, so depth is 1, not 2
    val f = Formula.And(
      Formula.Exists("x", atom("p", Var("x"))),
      Formula.Exists("y", atom("q", Var("y")))
    )
    assertEquals(FragmentCheck.check(f, Fragment.Screening(1)), Right(()))

  test("found reports the maximum nesting depth across the whole tree"):
    // one branch nests three deep; found must be 3
    val deep = Formula.Forall("x", Formula.Forall("y", Formula.Forall("z", atom("r", Var("x")))))
    val f = Formula.And(atom("p", Var("w")), deep)
    assertEquals(
      FragmentCheck.check(f, Fragment.Screening(1)),
      Left(FragmentViolation.QuantifierDepthExceeded(1, 3))
    )

  test("screening bounds quantifiers only, not function applications"):
    // ∀x. gt(f(x), 0): a Fn is present but screening ignores it; depth 1 ≤ 1
    val f = Formula.Forall("x", atom("gt", Fn("f", List(Var("x"))), Const("0")))
    assertEquals(FragmentCheck.check(f, Fragment.Screening(1)), Right(()))

  test("negative k rejects even a quantifier-free formula (total, degenerate)"):
    assertEquals(
      FragmentCheck.check(atom("p", Var("x")), Fragment.Screening(-1)),
      Left(FragmentViolation.QuantifierDepthExceeded(-1, 0))
    )

  // ==================== End-to-end: FOLParser.parse → FragmentCheck.check ====================

  private def parsed(s: String): Formula[FOL] =
    FOLParser.parse(s).fold(e => fail(s"parse failed for [$s]: $e"), identity)

  test("e2e: a plain predicate application is in Targeting"):
    assertEquals(FragmentCheck.check(parsed("P(x)"), Fragment.Targeting), Right(()))

  test("e2e: a parsed literal argument is a Const, so it stays in Targeting"):
    // `5` parses to Term.Const; the atom has no function application
    assertEquals(FragmentCheck.check(parsed("x < 5"), Fragment.Targeting), Right(()))

  test("e2e: a parsed quantifier leaves Targeting"):
    assertEquals(
      FragmentCheck.check(parsed("forall x . P(x)"), Fragment.Targeting),
      Left(FragmentViolation.QuantifierNotAllowed)
    )

  test("e2e: parsed arithmetic is a function application, rejected by Targeting"):
    // `2 * x + 3 = y` → the `=` atom's first term is the Fn `+`
    assertEquals(
      FragmentCheck.check(parsed("2 * x + 3 = y"), Fragment.Targeting),
      Left(FragmentViolation.FunctionApplicationNotAllowed("+"))
    )

  test("e2e: nested parsed quantifiers screen at depth 2 but not depth 1"):
    val f = parsed("forall x. exists y. x < y")
    assertEquals(FragmentCheck.check(f, Fragment.Screening(2)), Right(()))
    assertEquals(
      FragmentCheck.check(f, Fragment.Screening(1)),
      Left(FragmentViolation.QuantifierDepthExceeded(1, 2))
    )
