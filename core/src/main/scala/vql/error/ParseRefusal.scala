package vql.error

import lexer.{Found, GrammarSymbol, LexFailure, TokenKind, WordText}
import parser.SyntaxFailure

/** What the parser was about to read when it needed a symbol. */
enum ParseContext(val label: String):
  case RangeFormula         extends ParseContext("range formula")
  case ScopeFormula         extends ParseContext("scope formula")
  case AnswerVariables      extends ParseContext("answer variables")
  case EndOfAnswerVariables extends ParseContext("end of answer variables")
  case Denominator          extends ParseContext("denominator")
  case ToleranceOrEnd       extends ParseContext("tolerance or end")

/** The integer of `Q[op]^{k/n}` the parser was reading. */
enum QuantifierPart(val label: String):
  case Numerator   extends QuantifierPart("numerator k")
  case Denominator extends QuantifierPart("denominator n")

/** Why `VagueQueryParser.parse` refused a query's text. One case per sentence template. */
enum ParseRefusal:
  case QuantifierExpected(found: List[TokenKind])
  case UnknownQuantifierOperator(found: TokenKind)
  case IntegerExpected(part: QuantifierPart, found: Found)
  case IntegerOutOfRange(part: QuantifierPart, digits: WordText)

  /** The threshold `k/n` is no proportion: `n <= 0`, or `k` outside `[0, n]`. */
  case FractionOutOfRange(numerator: Int, denominator: Int)

  /** The tolerance lies outside `[0, 1]`. */
  case ToleranceOutOfRange(tolerance: Double)

  case VariableExpected(found: Found)
  case AnswerVariableExpected(found: Found)
  case SymbolExpected(expected: GrammarSymbol, before: ParseContext, found: Found)
  case TrailingTokens(first: TokenKind, rest: List[TokenKind])
  case Formula(failure: SyntaxFailure)
  case Lexical(failure: LexFailure)

  def message: String = this match
    case QuantifierExpected(found)                        =>
      s"Expected quantifier Q[op]^{k/n}, got: ${found.map(_.label).mkString(" ")}"
    case UnknownQuantifierOperator(found)                 =>
      s"Invalid quantifier operator: ${found.label} (expected ~, >=, or <=)"
    case IntegerExpected(part, found)                     =>
      s"Expected integer for ${part.label}, got${ParseRefusal.foundText(found)}"
    case IntegerOutOfRange(part, digits)                  =>
      s"Integer out of range for ${part.label}: ${digits.value}"
    case FractionOutOfRange(numerator, denominator)       =>
      s"Fraction $numerator/$denominator must lie in [0, 1] with a positive denominator"
    case ToleranceOutOfRange(tolerance)                   => s"Tolerance $tolerance must lie in [0, 1]"
    case VariableExpected(found)                          =>
      s"Expected variable identifier, got${ParseRefusal.foundText(found)}"
    case AnswerVariableExpected(found)                    =>
      s"Expected variable in list, got${ParseRefusal.foundText(found)}"
    case SymbolExpected(expected, before, Found.Token(k)) =>
      s"Expected '${expected.text}' before ${before.label}, got '${k.label}'"
    case SymbolExpected(expected, before, Found.EndOfInput) =>
      s"Expected '${expected.text}' before ${before.label}, got end of input"
    case TrailingTokens(first, rest)                      =>
      s"Unexpected tokens after query: ${(first :: rest).map(_.label).mkString(" ")}"
    case Formula(failure)                                 => failure.message
    case Lexical(failure)                                 => s"Lexer error: ${failure.message}"

object ParseRefusal:

  /** The tail of a "got" sentence: `: <label>`, or ` end of input`. */
  private def foundText(found: Found): String = found match
    case Found.Token(kind) => s": ${kind.label}"
    case Found.EndOfInput  => " end of input"
