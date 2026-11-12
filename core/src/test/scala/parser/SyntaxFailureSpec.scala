package parser

import lexer.{Found, GrammarSymbol, TokenKind, WordText}
import munit.FunSuite

class SyntaxFailureSpec extends FunSuite:

  private val word: Found       = Found.Token(TokenKind.Word(WordText("x")))
  private val rParen: TokenKind = TokenKind.Symbol(GrammarSymbol.RParen)

  test("FormulaExpected"):
    assertEquals(SyntaxFailure.FormulaExpected.message, "formula expected")

  test("QuantifierBodyExpected"):
    assertEquals(SyntaxFailure.QuantifierBodyExpected(Found.EndOfInput).message, "Body of quantified term expected")
    assertEquals(SyntaxFailure.QuantifierBodyExpected(word).message, "Body of quantified term expected, got: x")

  test("AtomExpected"):
    assertEquals(SyntaxFailure.AtomExpected(Found.EndOfInput).message, "parse_atom: expected atom")
    assertEquals(SyntaxFailure.AtomExpected(word).message, "parse_atom")

  test("TermExpected"):
    assertEquals(SyntaxFailure.TermExpected(Found.EndOfInput).message, "term expected")
    assertEquals(SyntaxFailure.TermExpected(word).message, "term expected, got: x")

  test("ClosingBracketExpected"):
    assertEquals(
      SyntaxFailure.ClosingBracketExpected(rParen, Found.EndOfInput).message,
      "Closing bracket ')' expected, but got: end of input",
    )
    assertEquals(
      SyntaxFailure.ClosingBracketExpected(rParen, word).message,
      "Closing bracket ')' expected, but got: x",
    )

  test("NotAnInfixAtom"):
    assertEquals(SyntaxFailure.NotAnInfixAtom.message, "Not an infix atom")

end SyntaxFailureSpec
