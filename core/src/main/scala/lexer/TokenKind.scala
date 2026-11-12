package lexer

import util.StringUtil

/** Punctuation and the operators the foundation grammar recognises. `text` is the surface form. */
enum GrammarSymbol(val text: String):
  case LParen       extends GrammarSymbol("(")
  case RParen       extends GrammarSymbol(")")
  case LBracket     extends GrammarSymbol("[")
  case RBracket     extends GrammarSymbol("]")
  case LBrace       extends GrammarSymbol("{")
  case RBrace       extends GrammarSymbol("}")
  case Comma        extends GrammarSymbol(",")
  case Dot          extends GrammarSymbol(".")
  case And          extends GrammarSymbol("/\\")
  case Or           extends GrammarSymbol("\\/")
  case Implies      extends GrammarSymbol("==>")
  case Iff          extends GrammarSymbol("<=>")
  case Not          extends GrammarSymbol("~")
  case Equal        extends GrammarSymbol("=")
  case Less         extends GrammarSymbol("<")
  case LessEqual    extends GrammarSymbol("<=")
  case Greater      extends GrammarSymbol(">")
  case GreaterEqual extends GrammarSymbol(">=")
  case Cons         extends GrammarSymbol("::")
  case Plus         extends GrammarSymbol("+")
  case Minus        extends GrammarSymbol("-")
  case Times        extends GrammarSymbol("*")
  case Divide       extends GrammarSymbol("/")
  case Power        extends GrammarSymbol("^")

object GrammarSymbol:

  private val byText: Map[String, GrammarSymbol] = values.map(s => s.text -> s).toMap

  /** The symbol whose surface form is `text`, if any. */
  def fromText(text: String): Option[GrammarSymbol] = byText.get(text)

/**
 * What a token is, as a refusal carries it. Text is held only where its content is bounded.
 *
 * {{{
 * TokenKind.of(Token.OpSym(">="))  // Symbol(GrammarSymbol.GreaterEqual)
 * TokenKind.of(Token.OpSym("~#"))  // UnknownOperator
 * TokenKind.of(Token.OpSym("≥"))   // StrayCharacter
 * }}}
 */
enum TokenKind:
  /** A run of `StringUtil.alphanumeric` characters. */
  case Word(text: WordText)

  /** `digits.digits`, the token `VagueQueryParser.mergeDecimalTokens` builds. */
  case Decimal(text: DecimalText)

  /** The content of a quoted literal, within the literal alphabet. */
  case Literal(content: LiteralText)

  case Symbol(symbol: GrammarSymbol)

  /** A run of `StringUtil.symbolic` characters that is no `GrammarSymbol`. */
  case UnknownOperator

  /** A character outside every lexer class. */
  case StrayCharacter

  def label: String = this match
    case Word(text)       => text.value
    case Decimal(text)    => text.value
    case Literal(content) => "\"" + content.value + "\""
    case Symbol(symbol)   => symbol.text
    case UnknownOperator  => "unknown operator"
    case StrayCharacter   => "unexpected character"

object TokenKind:

  /** Classifies a token. Throws `IllegalArgumentException` for text no `Lexer.lex` token holds. */
  def of(token: Token): TokenKind = token match
    case Token.Word(s) if StringUtil.isDecimalLiteral(s) => Decimal(DecimalText(s))
    case Token.Word(s)                                   => Word(WordText(s))
    case Token.StringLit(content)                        => Literal(LiteralText(content))
    case Token.OpSym(s)                                  =>
      GrammarSymbol.fromText(s) match
        case Some(symbol)                                      => Symbol(symbol)
        case None if s.nonEmpty && StringUtil.symbolic(s.head) => UnknownOperator
        case None                                              => StrayCharacter
    case Token.LParen                                    => Symbol(GrammarSymbol.LParen)
    case Token.RParen                                    => Symbol(GrammarSymbol.RParen)
    case Token.LBracket                                  => Symbol(GrammarSymbol.LBracket)
    case Token.RBracket                                  => Symbol(GrammarSymbol.RBracket)
    case Token.LBrace                                    => Symbol(GrammarSymbol.LBrace)
    case Token.RBrace                                    => Symbol(GrammarSymbol.RBrace)
    case Token.Comma                                     => Symbol(GrammarSymbol.Comma)
    case Token.Dot                                       => Symbol(GrammarSymbol.Dot)

/** What a parser found where its rule needed something else. */
enum Found:
  case Token(kind: TokenKind)
  case EndOfInput

object Found:

  /** The first of `tokens`, or `EndOfInput` when there is none. */
  def of(tokens: List[lexer.Token]): Found = tokens match
    case Nil       => EndOfInput
    case head :: _ => Token(TokenKind.of(head))
