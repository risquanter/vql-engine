package parser

/** Parse failure at a [[FOLParser]] string-input boundary.
  *
  * The OCaml-ported foundation parser signals failure internally by throwing
  * (ADR-007 C2/C12). The public string entries in [[FOLParser]] catch those
  * throws once, at the boundary, and return one of these variants instead — so
  * no exception escapes the public API.
  *
  * Foundation-local: no `fol.*` import, so the FOL core stays independently
  * usable (ADR-004). A consumer needing a richer error (e.g. `QueryError`)
  * maps this at its own boundary — matching on the variant to choose its own
  * error/status.
  */
enum ParseError:
  /** Malformed input: no formula could be built from a prefix of the tokens. */
  case Syntax(detail: String)

  /** A formula parsed successfully, but tokens remained unconsumed. */
  case Trailing(remaining: String)

  /** Lexing failed before parsing (e.g. an unterminated string literal). */
  case Lex(detail: String)

  /** A human-readable message, uniform across variants (for logging). */
  def message: String = this match
    case Syntax(d)   => d
    case Trailing(r) => s"Unparsed input: $r"
    case Lex(d)      => d
