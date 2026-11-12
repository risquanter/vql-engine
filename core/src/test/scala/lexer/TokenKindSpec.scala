package lexer

import munit.FunSuite
import parser.Combinators.tokenLabel

class TokenKindSpec extends FunSuite:

  private val punctuation: List[(Token, GrammarSymbol)] = List(
    Token.LParen   -> GrammarSymbol.LParen,
    Token.RParen   -> GrammarSymbol.RParen,
    Token.LBracket -> GrammarSymbol.LBracket,
    Token.RBracket -> GrammarSymbol.RBracket,
    Token.LBrace   -> GrammarSymbol.LBrace,
    Token.RBrace   -> GrammarSymbol.RBrace,
    Token.Comma    -> GrammarSymbol.Comma,
    Token.Dot      -> GrammarSymbol.Dot,
  )

  private val operators: List[GrammarSymbol] =
    GrammarSymbol.values.toList.filterNot(s => punctuation.exists(_._2 == s))

  private val unproducibleNames: List[String] = List("", "risk-id", "a b")

  // ── the three text types ──────────────────────────────────────────────

  test("WordText holds a non-empty alphanumeric run, apostrophe included"):
    assertEquals(WordText("it's_2").value, "it's_2")
    for bad <- List("", "a b", "a-b", "a.b") do intercept[IllegalArgumentException](WordText(bad))

  test("DecimalText holds digits, a dot and digits"):
    assertEquals(DecimalText("0.05").value, "0.05")
    for bad <- List("", "12", "1.", ".5", "1.2.3") do intercept[IllegalArgumentException](DecimalText(bad))

  test("LiteralText holds the literal alphabet, empty included"):
    assertEquals(LiteralText("IT Risk/sub-1_v2.0+").value, "IT Risk/sub-1_v2.0+")
    assertEquals(LiteralText("").value, "")
    for bad <- List("a<b", "it's", "a,b", "a\tb") do intercept[IllegalArgumentException](LiteralText(bad))

  // ── TokenKind.of ──────────────────────────────────────────────────────

  test("TokenKind.of classifies words, merged decimals and literals"):
    assertEquals(TokenKind.of(Token.Word("x")), TokenKind.Word(WordText("x")))
    assertEquals(TokenKind.of(Token.Word("0.5")), TokenKind.Decimal(DecimalText("0.5")))
    assertEquals(TokenKind.of(Token.StringLit("IT Risk")), TokenKind.Literal(LiteralText("IT Risk")))

  test("TokenKind.of maps each punctuation token to its GrammarSymbol"):
    for (token, symbol) <- punctuation do assertEquals(TokenKind.of(token), TokenKind.Symbol(symbol))

  test("TokenKind.of maps each known operator run to its GrammarSymbol"):
    for symbol <- operators do
      assertEquals(TokenKind.of(Token.OpSym(symbol.text)), TokenKind.Symbol(symbol))

  test("TokenKind.of classifies an unknown operator run and a stray character"):
    assertEquals(TokenKind.of(Token.OpSym("~#")), TokenKind.UnknownOperator)
    assertEquals(TokenKind.of(Token.OpSym("≥")), TokenKind.StrayCharacter)

  test("GrammarSymbol.fromText inverts text for every case"):
    for symbol <- GrammarSymbol.values do assertEquals(GrammarSymbol.fromText(symbol.text), Some(symbol))
    assertEquals(GrammarSymbol.fromText("~#"), None)

  test("label equals tokenLabel for words, decimals, literals and symbols"):
    val tokens =
      List(Token.Word("x"), Token.Word("0.5"), Token.StringLit("a b")) ++
        punctuation.map(_._1) ++ operators.map(s => Token.OpSym(s.text))
    for token <- tokens do assertEquals(TokenKind.of(token).label, tokenLabel(token))

  test("label names an unknown operator and a stray character without their text"):
    assertEquals(TokenKind.UnknownOperator.label, "unknown operator")
    assertEquals(TokenKind.StrayCharacter.label, "unexpected character")

  test("Found.of reads the first token, or the end of input"):
    assertEquals(Found.of(List(Token.Comma, Token.Dot)), Found.Token(TokenKind.Symbol(GrammarSymbol.Comma)))
    assertEquals(Found.of(Nil), Found.EndOfInput)

  // ── VariableText and SymbolText ───────────────────────────────────────

  test("VariableText.of reads a word and a decimal"):
    assertEquals(VariableText.of("x"), VariableText.Word(WordText("x")))
    assertEquals(VariableText.of("0.5"), VariableText.Decimal(DecimalText("0.5")))
    assertEquals(VariableText.of("0.5").value, "0.5")

  private val predicateOperators: List[String] = List("=", "<", "<=", ">", ">=")
  private val functionOperators: List[String]  = List("+", "-", "*", "/", "^", "::")

  test("SymbolText.of reads a word, a decimal and each name operator"):
    assertEquals(SymbolText.of("large"), SymbolText.Word(WordText("large")))
    assertEquals(SymbolText.of("0.5"), SymbolText.Decimal(DecimalText("0.5")))
    for text <- predicateOperators ++ functionOperators do
      assertEquals(SymbolText.of(text), SymbolText.Operator(GrammarSymbol.fromText(text).get))
      assertEquals(SymbolText.of(text).value, text)

  test("a predicate name admits the relations, and a function name the term operators"):
    for text <- predicateOperators do
      assert(SymbolText.admitsPredicate(text), text)
      assert(!SymbolText.admitsFunction(text), text)
    for text <- functionOperators do
      assert(SymbolText.admitsFunction(text), text)
      assert(!SymbolText.admitsPredicate(text), text)
    for name <- List("large", "0.5", "it's") do
      assert(SymbolText.admitsPredicate(name) && SymbolText.admitsFunction(name), name)

  test("no operator outside the name operators is a symbol name"):
    for text <- List("(", ",", ".", "/\\", "==>", "~") do
      assert(!SymbolText.admitsPredicate(text) && !SymbolText.admitsFunction(text), text)
      intercept[IllegalArgumentException](SymbolText.of(text))

  test("names no query text can produce are not admitted, and of throws for them"):
    for name <- unproducibleNames do
      assert(!VariableText.admits(name), name)
      assert(!SymbolText.admitsPredicate(name) && !SymbolText.admitsFunction(name), name)
      intercept[IllegalArgumentException](VariableText.of(name))
      intercept[IllegalArgumentException](SymbolText.of(name))

  test("LiteralText.admits matches what LiteralText accepts"):
    assert(LiteralText.admits("IT Risk"))
    assert(!LiteralText.admits("a<b"))
    intercept[IllegalArgumentException](LiteralText("a<b"))

end TokenKindSpec
