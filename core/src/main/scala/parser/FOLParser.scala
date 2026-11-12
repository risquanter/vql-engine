package parser

import lexer.{Lexer, LexerError, Token, TokenKind}
import logic.{FOL, Formula}
import parser.Combinators.{ParseFailure, SyntaxError}
import util.StringUtil.explode

/**
 * Complete FOL parser API.
 *
 * Public entry point that wires together the lexer, term parser, formula parser, and FOL atom
 * parser.
 *
 * The string-input entries return `Either[ParseError, Formula[FOL]]`: a single `try/catch` at each
 * entry converts the foundation's own exceptions (ADR-007 C2/C12) into a typed value, so none of
 * them escapes the public API. The failure is classified into a [[ParseError]] variant — `Lex`,
 * `Trailing`, or `Syntax`. `parseTokens` keeps the combinator tuple shape (C1) for callers that lex
 * themselves.
 *
 * OCaml equivalent: let parse = make_parser (parse_formula (parse_infix_atom,parse_atom) []) let
 * default_parser = parse
 */
object FOLParser:

  /** Parse an FOL formula from a string, using the default lexer. */
  def parse(s: String): Either[ParseError, Formula[FOL]] =
    parseWithLexer(s, Lexer.lex)

  /** Alternative name for [[parse]] (mirrors the OCaml `default_parser`). */
  def defaultParser(s: String): Either[ParseError, Formula[FOL]] = parse(s)

  /** Parse from a token list — combinator entry, tuple shape (ADR-007 C1). */
  def parseTokens(tokens: List[Token]): (Formula[FOL], List[Token]) =
    FOLAtomParser.parse(tokens)

  /**
   * Parse with a custom lexer.
   *
   * `Left(ParseError.Lex)` on a lexer error, with the offset of the refused character.
   * `Left(ParseError.Trailing)` when a formula parses but tokens remain. `Left(ParseError.Syntax)`
   * when a parser throws its own failure. Any other exception, including one the custom `lexer`
   * throws, propagates.
   */
  def parseWithLexer(
    s: String,
    lexer: List[Char] => List[Token],
  ): Either[ParseError, Formula[FOL]] =
    try
      val tokens         = lexer(explode(s))
      val (result, rest) = parseTokens(tokens)
      rest match
        case Nil           => Right(result)
        case first :: more =>
          Left(ParseError.Trailing(TokenKind.of(first), more.map(TokenKind.of), SourcePosition.Unknown))
    catch
      case e: LexerError   => Left(ParseError.Lex(e.failure, SourcePosition.At(s.length - e.remaining)))
      case e: ParseFailure => Left(ParseError.Syntax(e.failure, SourcePosition.Unknown))
      case e: SyntaxError  => Left(ParseError.Syntax(e.failure, SourcePosition.Unknown))

end FOLParser
