package parser

import munit.FunSuite
import logic.{FOL, Term, Formula}
import logic.Term.*
import logic.Formula.*
import parser.FOLParser

class FOLParserSpec extends FunSuite:

  /** Unwrap a parse expected to succeed. */
  private def parseOk(s: String): Formula[FOL] =
    FOLParser.parse(s).fold(e => fail(s"parse failed: ${e.message}"), identity)

  test("parse simple predicate") {
    val formula = parseOk("P(x)")
    assertEquals(formula, Atom(FOL("P", List(Var("x")))))
  }

  test("parse infix relation") {
    val formula = parseOk("x < y")
    assertEquals(formula, Atom(FOL("<", List(Var("x"), Var("y")))))
  }

  test("parse quantified formula") {
    val formula = parseOk("forall x . P(x)")
    assertEquals(formula, Forall("x", Atom(FOL("P", List(Var("x"))))))
  }

  test("parse complex formula from documentation") {
    val formula = parseOk("forall x. exists y. x < y")
    assertEquals(
      formula,
      Forall("x", Exists("y", Atom(FOL("<", List(Var("x"), Var("y"))))))
    )
  }

  test("parse formula with conjunction and implication") {
    val formula = parseOk("P(x) /\\ Q(y) ==> R(x, y)")
    assertEquals(
      formula,
      Imp(
        And(
          Atom(FOL("P", List(Var("x")))),
          Atom(FOL("Q", List(Var("y"))))
        ),
        Atom(FOL("R", List(Var("x"), Var("y"))))
      )
    )
  }

  test("parse arithmetic relation") {
    val formula = parseOk("2 * x + 3 = y")
    assertEquals(
      formula,
      Atom(FOL("=", List(
        Fn("+", List(
          Fn("*", List(Const("2"), Var("x"))),
          Const("3")
        )),
        Var("y")
      )))
    )
  }

  test("parse with true/false constants") {
    val formula = parseOk("P(x) \\/ false")
    assertEquals(formula, Or(Atom(FOL("P", List(Var("x")))), False))
  }

  test("parse nested quantifiers") {
    val formula = parseOk("forall x y. exists z. x < z /\\ y < z")
    assertEquals(
      formula,
      Forall("x", Forall("y", Exists("z",
        And(
          Atom(FOL("<", List(Var("x"), Var("z")))),
          Atom(FOL("<", List(Var("y"), Var("z"))))
        )
      )))
    )
  }

  test("parse negation") {
    val formula = parseOk("~ P(x)")
    assertEquals(formula, Not(Atom(FOL("P", List(Var("x"))))))
  }

  test("parse bi-implication") {
    val formula = parseOk("P(x) <=> Q(x)")
    assertEquals(
      formula,
      Iff(
        Atom(FOL("P", List(Var("x")))),
        Atom(FOL("Q", List(Var("x"))))
      )
    )
  }

  test("parse returns Right for valid input") {
    assertEquals(FOLParser.parse("P(x)"), Right(Atom(FOL("P", List(Var("x"))))))
  }

  test("defaultParser returns Right for valid input") {
    assertEquals(FOLParser.defaultParser("P(x)"), Right(Atom(FOL("P", List(Var("x"))))))
  }

  test("parseTokens returns remaining tokens") {
    import lexer.Token.*
    val tokens = List(Word("P"), LParen, Word("x"), RParen, Word("extra"))
    val (formula, rest) = FOLParser.parseTokens(tokens)
    assertEquals(formula, Atom(FOL("P", List(Var("x")))))
    assertEquals(rest, List(Word("extra")))
  }

  test("parseTokens with empty remaining") {
    import lexer.Token.*
    val tokens = List(Word("P"), LParen, Word("x"), RParen)
    val (formula, rest) = FOLParser.parseTokens(tokens)
    assertEquals(formula, Atom(FOL("P", List(Var("x")))))
    assertEquals(rest, List())
  }

  // ── Either error contract: no exception escapes the string entries ──

  test("parse returns Left (Trailing) on unparsed trailing input") {
    FOLParser.parse("P(x) )") match
      case Left(ParseError.Trailing(rem)) => assert(rem.nonEmpty)
      case other                          => fail(s"expected Left(Trailing), got $other")
  }

  test("parse returns Left (Syntax) on empty input") {
    FOLParser.parse("") match
      case Left(ParseError.Syntax(_)) => ()
      case other                      => fail(s"expected Left(Syntax), got $other")
  }

  test("parse returns Left (Lex) on lexer error") {
    FOLParser.parse("\"unterminated") match
      case Left(ParseError.Lex(_)) => ()
      case other                   => fail(s"expected Left(Lex), got $other")
  }

  test("parse returns Left on incomplete quantifier") {
    assert(FOLParser.parse("forall x").isLeft)
  }

  test("defaultParser returns Left on malformed input") {
    assert(FOLParser.defaultParser("forall x").isLeft)
  }

  test("parseWithLexer returns Right for valid input") {
    val result = FOLParser.parseWithLexer("P(x)", lexer.Lexer.lex)
    assertEquals(result, Right(Atom(FOL("P", List(Var("x"))))))
  }

  test("parseWithLexer returns Left (Trailing) with remaining on trailing input") {
    FOLParser.parseWithLexer("P(x) extra", lexer.Lexer.lex) match
      case Left(ParseError.Trailing(rem)) => assert(rem.nonEmpty)
      case other                          => fail(s"expected Left(Trailing), got $other")
  }

  test("parseWithLexer returns Left on malformed input") {
    assert(FOLParser.parseWithLexer("forall x", lexer.Lexer.lex).isLeft)
  }

  test("parse real-world example: transitivity of less-than") {
    // forall x y z. x < y /\ y < z ==> x < z
    val formula = parseOk("forall x y z . x < y /\\ y < z ==> x < z")
    assertEquals(
      formula,
      Forall("x", Forall("y", Forall("z",
        Imp(
          And(
            Atom(FOL("<", List(Var("x"), Var("y")))),
            Atom(FOL("<", List(Var("y"), Var("z"))))
          ),
          Atom(FOL("<", List(Var("x"), Var("z"))))
        )
      )))
    )
  }

  test("parse real-world example: existence of intermediate element") {
    // forall x y. x < y ==> exists z. x < z /\ z < y
    val formula = parseOk("forall x y . x < y ==> exists z . x < z /\\ z < y")
    assertEquals(
      formula,
      Forall("x", Forall("y",
        Imp(
          Atom(FOL("<", List(Var("x"), Var("y")))),
          Exists("z",
            And(
              Atom(FOL("<", List(Var("x"), Var("z")))),
              Atom(FOL("<", List(Var("z"), Var("y"))))
            )
          )
        )
      ))
    )
  }

  test("parse equality with function symbols") {
    val formula = parseOk("f(x) = g(y, z)")
    assertEquals(
      formula,
      Atom(FOL("=", List(
        Fn("f", List(Var("x"))),
        Fn("g", List(Var("y"), Var("z")))
      )))
    )
  }

  test("parse complex arithmetic: (x + y) * z = w") {
    val formula = parseOk("( x + y ) * z = w")
    assertEquals(
      formula,
      Atom(FOL("=", List(
        Fn("*", List(
          Fn("+", List(Var("x"), Var("y"))),
          Var("z")
        )),
        Var("w")
      )))
    )
  }
