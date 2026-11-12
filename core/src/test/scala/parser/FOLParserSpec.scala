package parser

import lexer.{GrammarSymbol, LexFailure, TokenKind, WordText}
import logic.{FOL, Formula, Term}
import logic.Formula.*
import logic.Term.*
import munit.FunSuite
import parser.FOLParser

class FOLParserSpec extends FunSuite:

  /** Unwrap a parse expected to succeed. */
  private def parseOk(s: String): Formula[FOL] =
    FOLParser.parse(s).fold(e => fail(s"parse failed: ${e.message}"), identity)

  test("parse simple predicate") {
    val formula = parseOk("P(x)")
    assertEquals(formula, Atom(FOL("P", List(mkVar("x")))))
  }

  test("parse infix relation") {
    val formula = parseOk("x < y")
    assertEquals(formula, Atom(FOL("<", List(mkVar("x"), mkVar("y")))))
  }

  test("parse quantified formula") {
    val formula = parseOk("forall x . P(x)")
    assertEquals(formula, mkForall("x", Atom(FOL("P", List(mkVar("x"))))))
  }

  test("parse complex formula from documentation") {
    val formula = parseOk("forall x. exists y. x < y")
    assertEquals(
      formula,
      mkForall("x", mkExists("y", Atom(FOL("<", List(mkVar("x"), mkVar("y")))))),
    )
  }

  test("parse formula with conjunction and implication") {
    val formula = parseOk("P(x) /\\ Q(y) ==> R(x, y)")
    assertEquals(
      formula,
      Imp(
        And(
          Atom(FOL("P", List(mkVar("x")))),
          Atom(FOL("Q", List(mkVar("y")))),
        ),
        Atom(FOL("R", List(mkVar("x"), mkVar("y")))),
      ),
    )
  }

  test("parse arithmetic relation") {
    val formula = parseOk("2 * x + 3 = y")
    assertEquals(
      formula,
      Atom(
        FOL(
          "=",
          List(
            mkFn(
              "+",
              List(
                mkFn("*", List(mkConst("2"), mkVar("x"))),
                mkConst("3"),
              ),
            ),
            mkVar("y"),
          ),
        )
      ),
    )
  }

  test("parse with true/false constants") {
    val formula = parseOk("P(x) \\/ false")
    assertEquals(formula, Or(Atom(FOL("P", List(mkVar("x")))), False))
  }

  test("parse nested quantifiers") {
    val formula = parseOk("forall x y. exists z. x < z /\\ y < z")
    assertEquals(
      formula,
      mkForall(
        "x",
        mkForall(
          "y",
          mkExists(
            "z",
            And(
              Atom(FOL("<", List(mkVar("x"), mkVar("z")))),
              Atom(FOL("<", List(mkVar("y"), mkVar("z")))),
            ),
          ),
        ),
      ),
    )
  }

  test("parse negation") {
    val formula = parseOk("~ P(x)")
    assertEquals(formula, Not(Atom(FOL("P", List(mkVar("x"))))))
  }

  test("parse bi-implication") {
    val formula = parseOk("P(x) <=> Q(x)")
    assertEquals(
      formula,
      Iff(
        Atom(FOL("P", List(mkVar("x")))),
        Atom(FOL("Q", List(mkVar("x")))),
      ),
    )
  }

  test("parse returns Right for valid input") {
    assertEquals(FOLParser.parse("P(x)"), Right(Atom(FOL("P", List(mkVar("x"))))))
  }

  test("defaultParser returns Right for valid input") {
    assertEquals(FOLParser.defaultParser("P(x)"), Right(Atom(FOL("P", List(mkVar("x"))))))
  }

  test("parseTokens returns remaining tokens") {
    import lexer.Token.*
    val tokens          = List(Word("P"), LParen, Word("x"), RParen, Word("extra"))
    val (formula, rest) = FOLParser.parseTokens(tokens)
    assertEquals(formula, Atom(FOL("P", List(mkVar("x")))))
    assertEquals(rest, List(Word("extra")))
  }

  test("parseTokens with empty remaining") {
    import lexer.Token.*
    val tokens          = List(Word("P"), LParen, Word("x"), RParen)
    val (formula, rest) = FOLParser.parseTokens(tokens)
    assertEquals(formula, Atom(FOL("P", List(mkVar("x")))))
    assertEquals(rest, List())
  }

  // ── Either error contract: no exception escapes the string entries ──

  test("parse returns Left (Trailing) on unparsed trailing input") {
    assertEquals(
      FOLParser.parse("P(x) )"),
      Left(ParseError.Trailing(TokenKind.Symbol(GrammarSymbol.RParen), Nil, SourcePosition.Unknown)),
    )
  }

  test("parse returns Left (Syntax) on empty input") {
    assertEquals(
      FOLParser.parse(""),
      Left(ParseError.Syntax(SyntaxFailure.FormulaExpected, SourcePosition.Unknown)),
    )
  }

  test("parse returns Left (Lex) on lexer error, at the end of the input") {
    assertEquals(
      FOLParser.parse("\"unterminated"),
      Left(ParseError.Lex(LexFailure.UnterminatedLiteral, SourcePosition.At(13))),
    )
  }

  test("parse returns Left (Lex) at the offset of a refused literal character") {
    assertEquals(
      FOLParser.parse("P(\"a<b\")"),
      Left(ParseError.Lex(LexFailure.RefusedLiteralCharacter, SourcePosition.At(4))),
    )
  }

  test("parse returns Left (Lex) at the offset of a newline inside a literal") {
    assertEquals(
      FOLParser.parse("P(\"a\nb\")"),
      Left(ParseError.Lex(LexFailure.NewlineInLiteral, SourcePosition.At(4))),
    )
  }

  test("parse returns Left (Syntax) carrying the token a term was expected at") {
    assertEquals(
      FOLParser.parse("P(x,)"),
      Left(
        ParseError.Syntax(
          SyntaxFailure.TermExpected(lexer.Found.Token(TokenKind.Symbol(GrammarSymbol.RParen))),
          SourcePosition.Unknown,
        )
      ),
    )
  }

  test("parseWithLexer propagates an exception the custom lexer throws") {
    intercept[IllegalStateException] {
      FOLParser.parseWithLexer("P(x)", _ => throw new IllegalStateException("lexer defect"))
    }
  }

  test("parse returns Left on incomplete quantifier") {
    assert(FOLParser.parse("forall x").isLeft)
  }

  test("defaultParser returns Left on malformed input") {
    assert(FOLParser.defaultParser("forall x").isLeft)
  }

  test("parseWithLexer returns Right for valid input") {
    val result = FOLParser.parseWithLexer("P(x)", lexer.Lexer.lex)
    assertEquals(result, Right(Atom(FOL("P", List(mkVar("x"))))))
  }

  test("parseWithLexer returns Left (Trailing) with remaining on trailing input") {
    assertEquals(
      FOLParser.parseWithLexer("P(x) extra", lexer.Lexer.lex),
      Left(ParseError.Trailing(TokenKind.Word(WordText("extra")), Nil, SourcePosition.Unknown)),
    )
  }

  test("parseWithLexer returns Left on malformed input") {
    assert(FOLParser.parseWithLexer("forall x", lexer.Lexer.lex).isLeft)
  }

  test("parse real-world example: transitivity of less-than") {
    // forall x y z. x < y /\ y < z ==> x < z
    val formula = parseOk("forall x y z . x < y /\\ y < z ==> x < z")
    assertEquals(
      formula,
      mkForall(
        "x",
        mkForall(
          "y",
          mkForall(
            "z",
            Imp(
              And(
                Atom(FOL("<", List(mkVar("x"), mkVar("y")))),
                Atom(FOL("<", List(mkVar("y"), mkVar("z")))),
              ),
              Atom(FOL("<", List(mkVar("x"), mkVar("z")))),
            ),
          ),
        ),
      ),
    )
  }

  test("parse real-world example: existence of intermediate element") {
    // forall x y. x < y ==> exists z. x < z /\ z < y
    val formula = parseOk("forall x y . x < y ==> exists z . x < z /\\ z < y")
    assertEquals(
      formula,
      mkForall(
        "x",
        mkForall(
          "y",
          Imp(
            Atom(FOL("<", List(mkVar("x"), mkVar("y")))),
            mkExists(
              "z",
              And(
                Atom(FOL("<", List(mkVar("x"), mkVar("z")))),
                Atom(FOL("<", List(mkVar("z"), mkVar("y")))),
              ),
            ),
          ),
        ),
      ),
    )
  }

  test("parse equality with function symbols") {
    val formula = parseOk("f(x) = g(y, z)")
    assertEquals(
      formula,
      Atom(
        FOL(
          "=",
          List(
            mkFn("f", List(mkVar("x"))),
            mkFn("g", List(mkVar("y"), mkVar("z"))),
          ),
        )
      ),
    )
  }

  test("parse complex arithmetic: (x + y) * z = w") {
    val formula = parseOk("( x + y ) * z = w")
    assertEquals(
      formula,
      Atom(
        FOL(
          "=",
          List(
            mkFn(
              "*",
              List(
                mkFn("+", List(mkVar("x"), mkVar("y"))),
                mkVar("z"),
              ),
            ),
            mkVar("w"),
          ),
        )
      ),
    )
  }

end FOLParserSpec
