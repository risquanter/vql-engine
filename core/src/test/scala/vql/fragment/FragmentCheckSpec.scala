package vql.fragment

import lexer.{LiteralText, SymbolText}
import logic.{FOL, Formula, Term}
import munit.FunSuite
import parser.FOLParser
import vql.id.SymbolName

/**
 * [[FragmentCheck]] decides structural membership of a parsed `Formula[FOL]` in a [[Fragment]],
 * reporting the first violated rule through `check` and every violated rule through `violations`,
 * and lists a function's literal arguments through `literalArguments` (ADR-018). Unit tests work
 * over hand-built trees; the end-to-end sections drive the public entry path `FOLParser.parse` →
 * `FragmentCheck`.
 */
class FragmentCheckSpec extends FunSuite:

  private def atom(name: String, terms: Term*): Formula[FOL] =
    Formula.Atom(FOL(name, terms.toList))

  import Term.{mkConst, mkFn, mkVar}

  // ==================== Targeting: admit ====================

  test("targeting admits a bare atom over variables"):
    assertEquals(FragmentCheck.check(atom("p", mkVar("x")), Fragment.Targeting(Set.empty, Set.empty)), Right(()))

  test("targeting admits inline literals (Term.Const) and variables"):
    // p(x, 42): the literal is a Const, not a function application
    assertEquals(
      FragmentCheck.check(atom("p", mkVar("x"), mkConst("42")), Fragment.Targeting(Set.empty, Set.empty)),
      Right(()),
    )

  test("targeting admits connectives over admissible atoms"):
    val f = Formula.And(atom("p", mkVar("x")), Formula.Not(atom("q", mkVar("y"))))
    assertEquals(FragmentCheck.check(f, Fragment.Targeting(Set.empty, Set.empty)), Right(()))

  // ==================== Targeting: reject ====================

  test("targeting rejects a universal quantifier"):
    val f = Formula.mkForall("x", atom("p", mkVar("x")))
    assertEquals(
      FragmentCheck.check(f, Fragment.Targeting(Set.empty, Set.empty)),
      Left(FragmentViolation.QuantifierNotAllowed),
    )

  test("targeting rejects an existential quantifier"):
    val f = Formula.mkExists("x", atom("p", mkVar("x")))
    assertEquals(
      FragmentCheck.check(f, Fragment.Targeting(Set.empty, Set.empty)),
      Left(FragmentViolation.QuantifierNotAllowed),
    )

  test("targeting rejects a function-application term, naming the function"):
    val f = atom("p", mkFn("f", List(mkVar("x"))))
    assertEquals(
      FragmentCheck.check(f, Fragment.Targeting(Set.empty, Set.empty)),
      Left(FragmentViolation.FunctionApplicationNotAllowed(SymbolText.of("f"))),
    )

  test("targeting rejects an arithmetic operator term (it is a Term.Fn)"):
    // gt(x + y, z): `+` is a function application
    val f = atom("gt", mkFn("+", List(mkVar("x"), mkVar("y"))), mkVar("z"))
    assertEquals(
      FragmentCheck.check(f, Fragment.Targeting(Set.empty, Set.empty)),
      Left(FragmentViolation.FunctionApplicationNotAllowed(SymbolText.of("+"))),
    )

  test("targeting reports the outermost function of a nested application"):
    val f = atom("p", mkFn("f", List(mkFn("g", List(mkVar("x"))))))
    assertEquals(
      FragmentCheck.check(f, Fragment.Targeting(Set.empty, Set.empty)),
      Left(FragmentViolation.FunctionApplicationNotAllowed(SymbolText.of("f"))),
    )

  // ==================== Targeting: admitted functions ====================

  private val nodeId: Set[SymbolName] = Set(SymbolName("node_id"))

  test("targeting admits an application of an admitted function"):
    val f = atom("eq", mkVar("x"), mkFn("node_id", List(mkConst("a1"))))
    assertEquals(FragmentCheck.check(f, Fragment.Targeting(nodeId, Set.empty)), Right(()))

  test("targeting refuses a function nested inside an admitted one, naming it"):
    val f = atom("eq", mkVar("x"), mkFn("node_id", List(mkFn("p95", List(mkVar("y"))))))
    assertEquals(
      FragmentCheck.check(f, Fragment.Targeting(nodeId, Set.empty)),
      Left(FragmentViolation.FunctionApplicationNotAllowed(SymbolText.of("p95"))),
    )

  test("an admitted application on the left does not hide a quantifier on the right"):
    val f = Formula.And(
      atom("eq", mkVar("x"), mkFn("node_id", List(mkConst("a1")))),
      Formula.mkExists("y", atom("p", mkVar("y"))),
    )
    assertEquals(
      FragmentCheck.check(f, Fragment.Targeting(nodeId, Set.empty)),
      Left(FragmentViolation.QuantifierNotAllowed),
    )

  // ==================== Targeting: first-violation order (pre-order, left-to-right)
  // ====================

  test("the left operand's violation is reported before the right operand's"):
    // left has a Fn, right has a quantifier: the Fn (left) wins
    val f =
      Formula.And(atom("p", mkFn("f", List(mkVar("x")))), Formula.mkExists("y", atom("q", mkVar("y"))))
    assertEquals(
      FragmentCheck.check(f, Fragment.Targeting(Set.empty, Set.empty)),
      Left(FragmentViolation.FunctionApplicationNotAllowed(SymbolText.of("f"))),
    )

  test("a right-side quantifier is reported when the left operand is admissible"):
    val f = Formula.And(atom("p", mkVar("x")), Formula.mkExists("y", atom("q", mkVar("y"))))
    assertEquals(
      FragmentCheck.check(f, Fragment.Targeting(Set.empty, Set.empty)),
      Left(FragmentViolation.QuantifierNotAllowed),
    )

  test("a quantifier node is reported before a function application inside its body"):
    val f = Formula.mkForall("x", atom("p", mkFn("f", List(mkVar("x")))))
    assertEquals(
      FragmentCheck.check(f, Fragment.Targeting(Set.empty, Set.empty)),
      Left(FragmentViolation.QuantifierNotAllowed),
    )

  // ==================== Screening: depth ====================

  test("Screening(0) admits a quantifier-free formula"):
    assertEquals(FragmentCheck.check(atom("p", mkVar("x")), Fragment.Screening(0)), Right(()))

  test("Screening(0) rejects any quantifier, identical to targeting's quantifier rule"):
    val f = Formula.mkForall("x", atom("p", mkVar("x")))
    assertEquals(
      FragmentCheck.check(f, Fragment.Screening(0)),
      Left(FragmentViolation.QuantifierDepthExceeded(0, 1)),
    )

  test("Screening(1) admits one level of nesting"):
    val f = Formula.mkForall("x", atom("p", mkVar("x")))
    assertEquals(FragmentCheck.check(f, Fragment.Screening(1)), Right(()))

  test("Screening(1) rejects two nested quantifiers, reporting the actual depth"):
    val f = Formula.mkExists("x", Formula.mkExists("y", atom("r", mkVar("x"), mkVar("y"))))
    assertEquals(
      FragmentCheck.check(f, Fragment.Screening(1)),
      Left(FragmentViolation.QuantifierDepthExceeded(1, 2)),
    )

  test("Screening(2) admits two nested quantifiers"):
    val f = Formula.mkExists("x", Formula.mkExists("y", atom("r", mkVar("x"), mkVar("y"))))
    assertEquals(FragmentCheck.check(f, Fragment.Screening(2)), Right(()))

  test("side-by-side quantifiers do not add: only nesting counts"):
    // (∃x. p(x)) /\ (∃y. q(y)): each branch nests one deep, so depth is 1, not 2
    val f = Formula.And(
      Formula.mkExists("x", atom("p", mkVar("x"))),
      Formula.mkExists("y", atom("q", mkVar("y"))),
    )
    assertEquals(FragmentCheck.check(f, Fragment.Screening(1)), Right(()))

  test("found reports the maximum nesting depth across the whole tree"):
    // one branch nests three deep; found must be 3
    val deep = Formula.mkForall("x", Formula.mkForall("y", Formula.mkForall("z", atom("r", mkVar("x")))))
    val f    = Formula.And(atom("p", mkVar("w")), deep)
    assertEquals(
      FragmentCheck.check(f, Fragment.Screening(1)),
      Left(FragmentViolation.QuantifierDepthExceeded(1, 3)),
    )

  test("screening bounds quantifiers only, not function applications"):
    // ∀x. gt(f(x), 0): a Fn is present but screening ignores it; depth 1 ≤ 1
    val f = Formula.mkForall("x", atom("gt", mkFn("f", List(mkVar("x"))), mkConst("0")))
    assertEquals(FragmentCheck.check(f, Fragment.Screening(1)), Right(()))

  test("negative k rejects even a quantifier-free formula (total, degenerate)"):
    assertEquals(
      FragmentCheck.check(atom("p", mkVar("x")), Fragment.Screening(-1)),
      Left(FragmentViolation.QuantifierDepthExceeded(-1, 0)),
    )

  test("violations under Screening is the depth violation or nothing, and check agrees"):
    val f = Formula.mkExists("x", Formula.mkExists("y", atom("r", mkVar("x"), mkVar("y"))))
    assertEquals(
      FragmentCheck.violations(f, Fragment.Screening(1)),
      List(FragmentViolation.QuantifierDepthExceeded(1, 2)),
    )
    assertEquals(FragmentCheck.violations(f, Fragment.Screening(2)), Nil)
    assertEquals(FragmentCheck.check(f, Fragment.Screening(1)), Left(FragmentViolation.QuantifierDepthExceeded(1, 2)))
    assertEquals(FragmentCheck.check(f, Fragment.Screening(2)), Right(()))

  // ==================== End-to-end: FOLParser.parse → FragmentCheck.check ====================

  private def parsed(s: String): Formula[FOL] =
    FOLParser.parse(s).fold(e => fail(s"parse failed for [$s]: $e"), identity)

  test("e2e: a plain predicate application is in Targeting"):
    assertEquals(FragmentCheck.check(parsed("P(x)"), Fragment.Targeting(Set.empty, Set.empty)), Right(()))

  test("e2e: a parsed literal argument is a Const, so it stays in Targeting"):
    // `5` parses to Term.Const; the atom has no function application
    assertEquals(FragmentCheck.check(parsed("x < 5"), Fragment.Targeting(Set.empty, Set.empty)), Right(()))

  test("e2e: a parsed quantifier leaves Targeting"):
    assertEquals(
      FragmentCheck.check(parsed("forall x . P(x)"), Fragment.Targeting(Set.empty, Set.empty)),
      Left(FragmentViolation.QuantifierNotAllowed),
    )

  test("e2e: parsed arithmetic is a function application, rejected by Targeting"):
    // `2 * x + 3 = y` → the `=` atom's first term is the Fn `+`
    assertEquals(
      FragmentCheck.check(parsed("2 * x + 3 = y"), Fragment.Targeting(Set.empty, Set.empty)),
      Left(FragmentViolation.FunctionApplicationNotAllowed(SymbolText.of("+"))),
    )

  test("e2e: a parsed node_id application is admitted only when node_id is in the set"):
    val f = parsed("""eq(x, node_id("01HQ"))""")
    assertEquals(FragmentCheck.check(f, Fragment.Targeting(nodeId, Set.empty)), Right(()))
    assertEquals(
      FragmentCheck.check(f, Fragment.Targeting(Set.empty, Set.empty)),
      Left(FragmentViolation.FunctionApplicationNotAllowed(SymbolText.of("node_id"))),
    )

  test("e2e: nested parsed quantifiers screen at depth 2 but not depth 1"):
    val f = parsed("forall x. exists y. x < y")
    assertEquals(FragmentCheck.check(f, Fragment.Screening(2)), Right(()))
    assertEquals(
      FragmentCheck.check(f, Fragment.Screening(1)),
      Left(FragmentViolation.QuantifierDepthExceeded(1, 2)),
    )

  // ==================== End-to-end: forbidden predicates ====================

  private val mitigationState: Set[SymbolName] =
    Set(SymbolName("mitigate"), SymbolName("mitigated"), SymbolName("unmitigated"))

  private def forbidding(forbidden: Set[SymbolName]): Fragment = Fragment.Targeting(Set.empty, forbidden)

  private def predicateRefused(name: String): FragmentViolation =
    FragmentViolation.PredicateNotAllowed(SymbolText.of(name))

  test("e2e: a forbidden predicate is refused with its name as written, in any case of letters"):
    assertEquals(
      FragmentCheck.violations(parsed("""leaf(x) /\ mitigated(x)"""), forbidding(mitigationState)),
      List(predicateRefused("mitigated")),
    )
    assertEquals(
      FragmentCheck.violations(parsed("""leaf(x) /\ Mitigated(x)"""), forbidding(mitigationState)),
      List(predicateRefused("Mitigated")),
    )

  test("e2e: a predicate that is not forbidden is admitted, known or not"):
    assertEquals(FragmentCheck.violations(parsed("""leaf(x) /\ foo(x)"""), forbidding(mitigationState)), Nil)

  test("e2e: a forbidden predicate is found under a negation and on each side of an implication"):
    assertEquals(
      FragmentCheck.violations(parsed("~mitigated(x)"), forbidding(mitigationState)),
      List(predicateRefused("mitigated")),
    )
    assertEquals(
      FragmentCheck.violations(parsed("mitigated(x) ==> leaf(x)"), forbidding(mitigationState)),
      List(predicateRefused("mitigated")),
    )
    assertEquals(
      FragmentCheck.violations(parsed("leaf(x) ==> unmitigated(x)"), forbidding(mitigationState)),
      List(predicateRefused("unmitigated")),
    )

  test("e2e: a forbidden name written with a capital letter refuses the lower-case predicate"):
    assertEquals(
      FragmentCheck.violations(parsed("mitigated(x)"), forbidding(Set(SymbolName("Mitigated")))),
      List(predicateRefused("mitigated")),
    )

  test("e2e: check reports a forbidden predicate"):
    assertEquals(
      FragmentCheck.check(parsed("mitigate(x)"), forbidding(mitigationState)),
      Left(predicateRefused("mitigate")),
    )

  // ==================== End-to-end: every violation ====================

  test("e2e: violations lists every rule broken, in pre-order, walking below the quantifier"):
    val f = parsed("""forall y. leaf(x) /\ mitigated(x) /\ eq(x, node_named("Ops"))""")
    assertEquals(
      FragmentCheck.violations(f, Fragment.Targeting(nodeId, mitigationState)),
      List(
        FragmentViolation.QuantifierNotAllowed,
        predicateRefused("mitigated"),
        FragmentViolation.FunctionApplicationNotAllowed(SymbolText.of("node_named")),
      ),
    )

  test("e2e: violations descends into a refused application, outer function first"):
    assertEquals(
      FragmentCheck.violations(parsed("""gt(p95(node_named("A")), 0)"""), Fragment.Targeting(nodeId, Set.empty)),
      List(
        FragmentViolation.FunctionApplicationNotAllowed(SymbolText.of("p95")),
        FragmentViolation.FunctionApplicationNotAllowed(SymbolText.of("node_named")),
      ),
    )

  test("e2e: violations is empty for a formula in the fragment"):
    assertEquals(FragmentCheck.violations(parsed("leaf(x)"), Fragment.Targeting(nodeId, mitigationState)), Nil)

  test("e2e: violations counts occurrences, so a refused function applied twice is listed twice"):
    assertEquals(
      FragmentCheck.violations(parsed("""eq(x, f(y)) \/ eq(x, f(z))"""), Fragment.Targeting(Set.empty, Set.empty)),
      List(
        FragmentViolation.FunctionApplicationNotAllowed(SymbolText.of("f")),
        FragmentViolation.FunctionApplicationNotAllowed(SymbolText.of("f")),
      ),
    )

  test("e2e: an application in predicate position is an atom, so only its arguments can be refused functions"):
    val fragment = Fragment.Targeting(nodeId, Set(SymbolName("mitigated")))
    assertEquals(
      FragmentCheck.violations(parsed("""forall y. Mitigated(y) /\ gt(p95(node_named(y)), 0)"""), fragment),
      List(
        FragmentViolation.QuantifierNotAllowed,
        predicateRefused("Mitigated"),
        FragmentViolation.FunctionApplicationNotAllowed(SymbolText.of("p95")),
        FragmentViolation.FunctionApplicationNotAllowed(SymbolText.of("node_named")),
      ),
    )
    assertEquals(
      FragmentCheck.violations(parsed("""forall y. Mitigated(y) /\ p95(node_named(y))"""), fragment),
      List(
        FragmentViolation.QuantifierNotAllowed,
        predicateRefused("Mitigated"),
        FragmentViolation.FunctionApplicationNotAllowed(SymbolText.of("node_named")),
      ),
    )

  test("check returns the first element of violations for every hand-built targeting formula above"):
    val formulas = List(
      atom("p", mkVar("x")),
      atom("p", mkVar("x"), mkConst("42")),
      Formula.And(atom("p", mkVar("x")), Formula.Not(atom("q", mkVar("y")))),
      Formula.mkForall("x", atom("p", mkVar("x"))),
      Formula.mkExists("x", atom("p", mkVar("x"))),
      atom("p", mkFn("f", List(mkVar("x")))),
      atom("gt", mkFn("+", List(mkVar("x"), mkVar("y"))), mkVar("z")),
      atom("p", mkFn("f", List(mkFn("g", List(mkVar("x")))))),
      atom("eq", mkVar("x"), mkFn("node_id", List(mkConst("a1")))),
      atom("eq", mkVar("x"), mkFn("node_id", List(mkFn("p95", List(mkVar("y")))))),
      Formula.And(
        atom("eq", mkVar("x"), mkFn("node_id", List(mkConst("a1")))),
        Formula.mkExists("y", atom("p", mkVar("y"))),
      ),
      Formula.And(atom("p", mkFn("f", List(mkVar("x")))), Formula.mkExists("y", atom("q", mkVar("y")))),
      Formula.And(atom("p", mkVar("x")), Formula.mkExists("y", atom("q", mkVar("y")))),
      Formula.mkForall("x", atom("p", mkFn("f", List(mkVar("x"))))),
    )
    for
      fragment <- List(Fragment.Targeting(Set.empty, Set.empty), Fragment.Targeting(nodeId, Set.empty))
      f        <- formulas
    do assertEquals(FragmentCheck.check(f, fragment), FragmentCheck.violations(f, fragment).headOption.toLeft(()), f)

  test("e2e: check returns the first element of violations for every targeting formula above"):
    val formulas = List(
      "P(x)",
      "x < 5",
      "forall x . P(x)",
      "2 * x + 3 = y",
      """eq(x, node_id("01HQ"))""",
      """forall y. leaf(x) /\ mitigated(x) /\ eq(x, node_named("Ops"))""",
      """gt(p95(node_named("A")), 0)""",
      """exists y. p(f(y)) /\ Mitigated(y)""",
    )
    val fragment = Fragment.Targeting(nodeId, mitigationState)
    formulas.foreach { s =>
      val f = parsed(s)
      assertEquals(FragmentCheck.check(f, fragment), FragmentCheck.violations(f, fragment).headOption.toLeft(()), s)
    }

  // ==================== End-to-end: literal arguments ====================

  test("e2e: literalArguments lists a function's literals left to right, repeats kept"):
    val f = parsed("""eq(x, node_id("B")) \/ child_of(x, node_id("A")) \/ eq(x, node_id("B"))""")
    assertEquals(
      FragmentCheck.literalArguments(f, SymbolName("node_id")),
      List(LiteralText("B"), LiteralText("A"), LiteralText("B")),
    )

  test("e2e: literalArguments is empty without an application, or when the argument is a variable"):
    assertEquals(FragmentCheck.literalArguments(parsed("leaf(x)"), SymbolName("node_id")), Nil)
    assertEquals(FragmentCheck.literalArguments(parsed("eq(x, node_id(y))"), SymbolName("node_id")), Nil)

  test("e2e: literalArguments finds an application nested inside another"):
    assertEquals(
      FragmentCheck.literalArguments(parsed("""eq(x, node_id(node_id("A")))"""), SymbolName("node_id")),
      List(LiteralText("A")),
    )

  test("e2e: literalArguments walks below a quantifier"):
    assertEquals(
      FragmentCheck.literalArguments(parsed("""forall y. eq(y, node_id("A"))"""), SymbolName("node_id")),
      List(LiteralText("A")),
    )

  // ==================== At the nesting limits ====================

  test("violations and literalArguments return on a formula at both nesting limits"):
    // 256 negations are 256 operators, and `p(` with 63 `f(`s nests 64 parentheses: both of ADR-024's limits
    val f        = parsed("~ " * 256 + "p(" + "f(" * 63 + "x" + ")" * 63 + ")")
    val fragment = Fragment.Targeting(Set(SymbolName("f")), Set(SymbolName("q")))
    assertEquals(FragmentCheck.violations(f, fragment), Nil)
    assertEquals(FragmentCheck.check(f, fragment), Right(()))
    assertEquals(FragmentCheck.literalArguments(f, SymbolName("f")), Nil)

  test("violations descends through refused applications nested to each nesting limit"):
    // 256 binary `-`s nest left 256 deep, 256 unary `-`s nest right 256 deep, and 63 `f(`s nest 64 parentheses
    val refusedMinus = FragmentViolation.FunctionApplicationNotAllowed(SymbolText.of("-"))
    val refusedF     = FragmentViolation.FunctionApplicationNotAllowed(SymbolText.of("f"))
    val nothing      = Fragment.Targeting(Set.empty, Set.empty)
    val binary       = parsed("p(" + List.fill(257)("x").mkString(" - ") + ")")
    val unary        = parsed("p(" + "- " * 256 + "x)")
    val applications = parsed("p(" + "f(" * 63 + "x" + ")" * 63 + ")")
    assertEquals(FragmentCheck.violations(binary, nothing), List.fill(256)(refusedMinus))
    assertEquals(FragmentCheck.violations(unary, nothing), List.fill(256)(refusedMinus))
    assertEquals(FragmentCheck.violations(applications, nothing), List.fill(63)(refusedF))

end FragmentCheckSpec
