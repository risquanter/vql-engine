package lexer

/** Why `Lexer.lex` refused its input. */
enum LexFailure:
  case UnterminatedLiteral
  case NewlineInLiteral
  case RefusedLiteralCharacter

  def message: String = this match
    case UnterminatedLiteral     => "Unterminated string literal: missing closing '\"'"
    case NewlineInLiteral        => "Unterminated string literal: newline before closing '\"'"
    case RefusedLiteralCharacter => "String literal holds a character outside its alphabet"
