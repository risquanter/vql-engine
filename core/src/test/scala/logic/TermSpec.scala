package logic

import Term.*
import munit.FunSuite

class TermSpec extends FunSuite:

  test("create variable") {
    val x = Var("x")
    x match
      case Var(name) => assertEquals(name, "x")
      case _         => fail("Should be Var")
  }

  test("create inline literal constant") {
    val c = Const("0")
    c match
      case Const(name) => assertEquals(name, "0")
      case _           => fail("Should be Const")
  }

  test("create zero-arity function application (pre-Const OCaml style)") {
    // Fn(name, Nil) is the OCaml-port representation for zero-arity symbols
    // (e.g. 'nil' list constant). Numeric literals now use Term.Const instead.
    val nilFn = Fn("nil", Nil)
    nilFn match
      case Fn(name, args) =>
        assertEquals(name, "nil")
        assertEquals(args, Nil)
      case _              => fail("Should be Fn with empty args")
  }

  test("create function application") {
    val fx = Fn("f", List(Var("x")))
    fx match
      case Fn(name, args) =>
        assertEquals(name, "f")
        assertEquals(args, List(Var("x")))
      case _              => fail("Should be Fn")
  }

  test("nested function application: f(x, g(y))") {
    val term = Fn(
      "f",
      List(
        Var("x"),
        Fn("g", List(Var("y"))),
      ),
    )

    term match
      case Fn("f", List(Var("x"), Fn("g", List(Var("y"))))) => () // success
      case _                                                => fail("Pattern match failed")
  }

  test("complex example from OCaml uses Const for numeric literals") {
    val example = Term.example
    // Top level: Fn("sqrt", ...)
    // Numeric literals inside: Const("1"), Const("2") — not Fn("1", Nil)
    example match
      case Fn("sqrt", List(Fn("-", List(Const("1"), _)))) => () // success
      case _ => fail("Expected sqrt(1 - ...) with Const(\"1\") literal")
  }

  test("arithmetic expression: 2 * x + 3") {
    val expr = Fn(
      "+",
      List(
        Fn("*", List(Fn("2", Nil), Var("x"))),
        Fn("3", Nil),
      ),
    )

    expr match
      case Fn(name, args) =>
        assertEquals(name, "+")
        assertEquals(args.length, 2)
      case _              => fail("Should be Fn")
  }

  // ── building functions ────────────────────────────────────────────

  private val unproducibleNames: List[String] = List("", "risk-id", "a b")

  test("mkVar builds the Var for a word and a decimal"):
    assertEquals(mkVar("x"), Var("x"))
    assertEquals(mkVar("0.5"), Var("0.5"))

  test("mkFn builds the Fn for a word, a decimal and a term operator"):
    assertEquals(mkFn("f", List(Var("x"))), Fn("f", List(Var("x"))))
    assertEquals(mkFn("0.5", Nil), Fn("0.5", Nil))
    for op <- List("+", "-", "*", "/", "^", "::") do assertEquals(mkFn(op, Nil), Fn(op, Nil))

  test("mkConst builds the Const for digits, a decimal, nil and literal content"):
    for c <- List("42", "0.05", "nil", "IT Risk", "") do assertEquals(mkConst(c), Const(c))

  test("each building function refuses a name no query text can produce"):
    for name <- unproducibleNames do
      intercept[IllegalArgumentException](mkVar(name))
      intercept[IllegalArgumentException](mkFn(name, Nil))
    intercept[IllegalArgumentException](mkConst("a<b"))

  test("mkFn refuses a relation and punctuation as a function name"):
    for op <- List("<", "=", "(", ",") do intercept[IllegalArgumentException](mkFn(op, Nil))

end TermSpec
