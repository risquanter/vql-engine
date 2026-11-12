package parser

import lexer.{LexFailure, TokenKind}

/**
 * Parse failure at a [[FOLParser]] string-input boundary.
 *
 * The OCaml-ported foundation parser signals failure internally by throwing (ADR-007 C2/C12). The
 * public string entries in [[FOLParser]] catch the parsers' own throws once, at the boundary, and
 * return one of these variants instead. Each variant carries the typed value its message is built
 * from.
 *
 * Foundation-local: no `vql.*` import, so the FOL core stays independently usable (ADR-004). A
 * consumer needing a richer error (e.g. `QueryError`) maps this at its own boundary, matching on
 * the variant to choose its own error or status.
 */
enum ParseError:
  /** Malformed input: no formula could be built from a prefix of the tokens. */
  case Syntax(failure: SyntaxFailure, position: SourcePosition)

  /** A formula parsed, but tokens remained. `first` is held apart, so at least one remains. */
  case Trailing(first: TokenKind, rest: List[TokenKind], position: SourcePosition)

  /** Lexing failed before parsing. */
  case Lex(failure: LexFailure, position: SourcePosition)

  /** Stored per case, so an abstract member the case parameters implement (ADR-006 §3). */
  def position: SourcePosition

  /** A human-readable message, uniform across variants (for logging). */
  def message: String = this match
    case Syntax(failure, _)       => failure.message
    case Trailing(first, rest, _) => s"Unparsed input: ${(first :: rest).map(_.label).mkString(" ")}"
    case Lex(failure, _)          => failure.message

end ParseError
