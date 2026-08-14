package parser

import scala.util.control.NonFatal

import lexer.{Lexer, LexerError, Token}
import logic.{FOL, Formula}
import parser.Combinators.tokensLabel
import util.StringUtil.explode

/**
 * Complete FOL parser API.
 *
 * Public entry point that wires together the lexer, term parser, formula parser, and FOL atom
 * parser.
 *
 * The string-input entries return `Either[ParseError, Formula[FOL]]`: a single `try/catch` at each
 * entry converts the foundation's internal backtracking exceptions (ADR-007 C2/C12) into a typed
 * value, so no exception escapes the public API. The failure is classified into a [[ParseError]]
 * variant — `Lex`, `Trailing`, or `Syntax`. `parseTokens` keeps the combinator tuple shape (C1) for
 * callers that lex themselves.
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
   * `Left(ParseError.Lex)` on a lexer error, `Left(ParseError.Trailing)` when a formula parses but
   * tokens remain, `Left(ParseError.Syntax)` on any other parse failure.
   */
  def parseWithLexer(
    s: String,
    lexer: List[Char] => List[Token],
  ): Either[ParseError, Formula[FOL]] =
    try
      val tokens         = lexer(explode(s))
      val (result, rest) = parseTokens(tokens)
      if rest.isEmpty then Right(result)
      else Left(ParseError.Trailing(tokensLabel(rest)))
    catch
      case e: LexerError => Left(ParseError.Lex(detailOf(e)))
      case NonFatal(e)   => Left(ParseError.Syntax(detailOf(e)))

  private def detailOf(e: Throwable): String =
    Option(e.getMessage).getOrElse(e.getClass.getSimpleName)

end FOLParser
