package parser

import lexer.{Found, TokenKind}

/**
 * Why a foundation parser could not build a term or formula. One case per throw template.
 *
 * `message` is the ported parser's `failwith` text, word for word.
 */
enum SyntaxFailure:
  case FormulaExpected
  case QuantifierBodyExpected(found: Found)
  case AtomExpected(found: Found)
  case TermExpected(found: Found)
  case ClosingBracketExpected(bracket: TokenKind, found: Found)
  case NotAnInfixAtom

  def message: String = this match
    case FormulaExpected                            => "formula expected"
    case QuantifierBodyExpected(Found.EndOfInput)   => "Body of quantified term expected"
    case QuantifierBodyExpected(Found.Token(kind))  =>
      s"Body of quantified term expected, got: ${kind.label}"
    case AtomExpected(Found.EndOfInput)             => "parse_atom: expected atom"
    case AtomExpected(Found.Token(_))               => "parse_atom"
    case TermExpected(Found.EndOfInput)             => "term expected"
    case TermExpected(Found.Token(kind))            => s"term expected, got: ${kind.label}"
    case ClosingBracketExpected(bracket, Found.EndOfInput)  =>
      s"Closing bracket '${bracket.label}' expected, but got: end of input"
    case ClosingBracketExpected(bracket, Found.Token(kind)) =>
      s"Closing bracket '${bracket.label}' expected, but got: ${kind.label}"
    case NotAnInfixAtom                             => "Not an infix atom"
