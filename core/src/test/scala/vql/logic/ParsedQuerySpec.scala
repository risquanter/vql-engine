package vql.logic

import lexer.VariableText
import logic.{FOL, Formula, Term}
import parser.SourcePosition
import munit.FunSuite
import vql.error.{QueryError, QueryException}
import vql.error.QueryError.ValidationProblem
import vql.quantifier.Quantifier

class ParsedQuerySpec extends FunSuite:

  import Formula.*
  import ParsedQuery.*
  import Quantifier.*
  import Term.*

  // ==================== Constructor Tests ====================

  test("create simple Boolean query") {
    val q = ParsedQuery(
      quantifier = mkAbout(1, 2),
      variable = "x",
      range = Atom(FOL("country", List(mkVar("x")))),
      scope = Atom(FOL("large", List(mkVar("x")))),
      answerVars = Nil,
    )
    assert(q.isBoolean)
    assert(!q.isUnary)
    assertEquals(q.variable, "x")
  }

  test("create unary query with answer variable") {
    val q = ParsedQuery(
      quantifier = mkAbout(1, 2),
      variable = "x",
      range = Atom(FOL("city", List(mkVar("x"), mkVar("y")))),
      scope = Atom(FOL("large", List(mkVar("x")))),
      answerVars = List("y"),
    )
    assert(!q.isBoolean)
    assert(q.isUnary)
    assertEquals(q.answerVars, List("y"))
  }

  test("create query with complex scope formula") {
    val q = ParsedQuery(
      quantifier = mkAtLeast(3, 4),
      variable = "x",
      range = Atom(FOL("country", List(mkVar("x")))),
      scope = mkExists(
        "y",
        And(
          Atom(FOL("hasGDP", List(mkVar("x"), mkVar("y")))),
          Atom(FOL("<", List(mkVar("y"), mkConst("20")))),
        ),
      ),
    )
    assertEquals(q.quantifier, mkAtLeast(3, 4))
  }

  // ==================== Variable Extraction Tests ====================

  test("extract range variables from unary relation") {
    val q = ParsedQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = Atom(FOL("country", List(mkVar("x")))),
      scope = True,
    )
    assertEquals(q.rangeVars, Set("x"))
  }

  test("extract range variables from binary relation") {
    val q = ParsedQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = Atom(FOL("city_of", List(mkVar("x"), mkVar("y")))),
      scope = True,
      answerVars = List("y"),
    )
    assertEquals(q.rangeVars, Set("x", "y"))
  }

  test("extract range variables with function terms") {
    val q = ParsedQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = Atom(
        FOL(
          "relation",
          List(
            mkFn("f", List(mkVar("x"))),
            mkVar("y"),
          ),
        )
      ),
      scope = True,
      answerVars = List("y"),
    )
    assertEquals(q.rangeVars, Set("x", "y"))
  }

  test("extract scope variables from simple atom") {
    val q = ParsedQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = Atom(FOL("country", List(mkVar("x")))),
      scope = Atom(FOL("large", List(mkVar("x")))),
    )
    assert(q.scopeVars.contains("x"))
  }

  test("extract scope variables from complex formula") {
    val q = example1 // Uses ∃y (hasGDP_agr(x,y) ∧ y≤20)
    assert(q.scopeVars.contains("x"))
    assert(q.scopeVars.contains("y"))
  }

  test("extract scope variables from nested quantifiers") {
    val q = ParsedQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = Atom(FOL("country", List(mkVar("x")))),
      scope = mkForall("y", mkExists("z", Atom(FOL("P", List(mkVar("x"), mkVar("y"), mkVar("z")))))),
    )
    assertEquals(q.scopeVars, Set("x", "y", "z"))
  }

  test("handle constants in scope") {
    val q = ParsedQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = Atom(FOL("country", List(mkVar("x")))),
      scope = Atom(FOL("capital", List(mkConst("Paris")))),
    )
    // Constants don't contribute to variables
    assert(!q.scopeVars.contains("Paris"))
  }

  // ==================== Validation Tests ====================

  test("mk validates quantified variable in range") {
    val error = intercept[QueryException] {
      mk(
        mkAbout(1, 2),
        "x",
        FOL("country", List(mkVar("y"))), // x not in range!
        True,
      )
    }
    assert(error.getMessage.contains("must occur free in the range"))
    assertEquals(
      error.error,
      QueryError.ValidationError(
        ValidationProblem.QuantifiedVariableNotInRange(VariableText.of("x"), Set(VariableText.of("y"))),
        SourcePosition.Unknown,
      ),
    )
  }

  test("mk validates range variables subset of answer vars") {
    val error = intercept[QueryException] {
      mk(
        mkAbout(1, 2),
        "x",
        FOL("city_of", List(mkVar("x"), mkVar("y"))), // y in range
        True,
        Nil,                                      // but y not in answer vars!
      )
    }
    assert(error.getMessage.contains("must be subset of answer variables"))
    assertEquals(
      error.error,
      QueryError.ValidationError(
        ValidationProblem.RangeVariablesNotAnswered(Set(VariableText.of("y")), Nil),
        SourcePosition.Unknown,
      ),
    )
  }

  test("mk accepts valid binary range with answer var") {
    val q = mk(
      mkAbout(1, 2),
      "x",
      FOL("city_of", List(mkVar("x"), mkVar("y"))),
      Atom(FOL("large", List(mkVar("x")))),
      List("y"),
    )
    assertEquals(q.rangeVars, Set("x", "y"))
  }

  test("mk accepts unary range with no answer vars") {
    val q = mk(
      mkAbout(1, 2),
      "x",
      FOL("country", List(mkVar("x"))),
      Atom(FOL("large", List(mkVar("x")))),
    )
    assert(q.isBoolean)
  }

  // ==================== Paper Examples Tests ====================

  test("example1: Boolean query structure") {
    val q1 = example1

    // Verify structure matches paper
    assertEquals(q1.variable, "x")
    q1.range match
      case Atom(fol) => assertEquals(fol.predicate, "country")
      case other     => fail(s"Expected atom range, got $other")
    assert(q1.isBoolean)

    // Verify quantifier
    assertEquals(Quantifier.targetProportion(q1.quantifier), 0.75)

    // Verify scope is ∃y (...)
    q1.scope match
      case Exists(y, And(_, _)) =>
        assertEquals(y, "y")
      case _                    =>
        fail("Expected ∃y (... ∧ ...)")
  }

  test("example1: variable extraction") {
    val q1 = example1
    assertEquals(q1.rangeVars, Set("x"))
    assert(q1.scopeVars.contains("x"))
    assert(q1.scopeVars.contains("y"))
  }

  test("example3Skeleton: unary query structure") {
    val q3 = example3Skeleton

    assertEquals(q3.variable, "x")
    assert(q3.isUnary)
    assertEquals(q3.answerVars, List("y"))

    // Verify quantifier is "about half"
    assertEquals(Quantifier.targetProportion(q3.quantifier), 0.5)
  }

  test("simpleExample: basic structure") {
    val q = simpleExample

    assert(q.isBoolean)
    assertEquals(q.variable, "x")
    assertEquals(Quantifier.targetProportion(q.quantifier), 0.5)

    // Verify scope is simple atom
    q.scope match
      case Atom(fol) =>
        assertEquals(fol.predicate, "large")
      case _         =>
        fail("Expected Atom(...)")
  }

  // ==================== Query Properties Tests ====================

  test("isBoolean returns true when no answer vars") {
    val q = ParsedQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = Atom(FOL("country", List(mkVar("x")))),
      scope = True,
    )
    assert(q.isBoolean)
  }

  test("isBoolean returns false when answer vars present") {
    val q = ParsedQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = Atom(FOL("city", List(mkVar("x"), mkVar("y")))),
      scope = True,
      answerVars = List("y"),
    )
    assert(!q.isBoolean)
  }

  test("isUnary returns true for single answer var") {
    val q = ParsedQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = Atom(FOL("city", List(mkVar("x"), mkVar("y")))),
      scope = True,
      answerVars = List("y"),
    )
    assert(q.isUnary)
  }

  test("isUnary returns false for multiple answer vars") {
    val q = ParsedQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = Atom(FOL("relation", List(mkVar("x"), mkVar("y"), mkVar("z")))),
      scope = True,
      answerVars = List("y", "z"),
    )
    assert(!q.isUnary)
  }

  test("isUnary returns false for no answer vars") {
    val q = simpleExample
    assert(!q.isUnary)
  }

  // ==================== Complex Query Tests ====================

  test("query with nested logical operators") {
    val q = ParsedQuery(
      quantifier = mkAtLeast(2, 3),
      variable = "x",
      range = Atom(FOL("country", List(mkVar("x")))),
      scope = And(
        Atom(FOL("large", List(mkVar("x")))),
        Or(
          Atom(FOL("wealthy", List(mkVar("x")))),
          Atom(FOL("educated", List(mkVar("x")))),
        ),
      ),
    )

    assert(q.scopeVars.contains("x"))
    assertEquals(q.rangeVars, Set("x"))
  }

  test("query with implication in scope") {
    val q = ParsedQuery(
      quantifier = almostAll,
      variable = "x",
      range = Atom(FOL("country", List(mkVar("x")))),
      scope = Imp(
        Atom(FOL("large", List(mkVar("x")))),
        Atom(FOL("diverse", List(mkVar("x")))),
      ),
    )

    assert(q.isBoolean)
    assertEquals(Quantifier.targetProportion(q.quantifier), 1.0)
  }

  test("query with multiple quantifiers in scope") {
    val q = ParsedQuery(
      quantifier = aboutTwoThirds,
      variable = "x",
      range = Atom(FOL("country", List(mkVar("x")))),
      scope = mkExists("y", mkForall("z", Atom(FOL("relation", List(mkVar("x"), mkVar("y"), mkVar("z")))))),
    )

    val vars = q.scopeVars
    assert(vars.contains("x"))
    assert(vars.contains("y"))
    assert(vars.contains("z"))
  }

  // ==================== Edge Cases ====================

  test("query with False scope") {
    val q = ParsedQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = Atom(FOL("country", List(mkVar("x")))),
      scope = False,
    )
    assertEquals(q.scopeVars, Set.empty)
  }

  test("query with True scope") {
    val q = ParsedQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = Atom(FOL("country", List(mkVar("x")))),
      scope = True,
    )
    assertEquals(q.scopeVars, Set.empty)
  }

  test("query with empty range (constant-only terms)") {
    val q = ParsedQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = Atom(FOL("relation", List(mkVar("x"), mkConst("USA")))),
      scope = True,
    )
    assertEquals(q.rangeVars, Set("x"))
  }

  // ==================== Names in a query ====================

  test("tree case constructors do not compile outside the package logic"):
    assert(compileErrors("""_root_.logic.Term.Var("x")""").nonEmpty)
    assert(compileErrors("""new _root_.logic.FOL("p", Nil)""").nonEmpty)
    assert(compileErrors("""_root_.logic.Term.mkVar("x") match { case v: _root_.logic.Term.Var => v.copy(name = "y") }""").nonEmpty)
    assert(compileErrors("""_root_.logic.Formula.Forall("x", _root_.logic.Formula.True)""").nonEmpty)
    assert(compileErrors("""_root_.logic.Formula.mkForall("x", _root_.logic.Formula.True)""").isEmpty)

  test("a query with a variable or answer variable no query text can produce throws"):
    val range = Atom(FOL("country", List(mkVar("x"))))
    intercept[IllegalArgumentException](ParsedQuery(mkAbout(1, 2), "risk-id", range, True, Nil))
    intercept[IllegalArgumentException](ParsedQuery(mkAbout(1, 2), "x", range, True, List("a b")))

end ParsedQuerySpec
