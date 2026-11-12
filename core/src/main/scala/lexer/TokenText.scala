package lexer

import util.StringUtil

/** The text of a word token: one or more `StringUtil.alphanumeric` characters. */
opaque type WordText = String

object WordText:

  def apply(value: String): WordText =
    require(value.nonEmpty && value.forall(StringUtil.alphanumeric), "WordText must be a non-empty alphanumeric run")
    value

  extension (text: WordText) def value: String = text

/** The text of a merged decimal token: digits, `.`, digits. */
opaque type DecimalText = String

object DecimalText:

  def apply(value: String): DecimalText =
    require(StringUtil.isDecimalLiteral(value), "DecimalText must be digits.digits")
    value

  extension (text: DecimalText) def value: String = text

/** The content of a quoted literal: zero or more characters of the literal alphabet. */
opaque type LiteralText = String

object LiteralText:

  /** Whether every character of `value` is in the literal alphabet. */
  def admits(value: String): Boolean = value.forall(Lexer.literalChars)

  def apply(value: String): LiteralText =
    require(admits(value), "LiteralText must stay within the literal alphabet")
    value

  extension (text: LiteralText) def value: String = text

/** The name of a variable: the text of a `Token.Word`. */
enum VariableText:
  case Word(text: WordText)
  case Decimal(text: DecimalText)

  def value: String = this match
    case Word(text)    => text.value
    case Decimal(text) => text.value

object VariableText:

  /** Whether `name` is text a `Token.Word` can hold. */
  def admits(name: String): Boolean =
    StringUtil.isDecimalLiteral(name) || (name.nonEmpty && name.forall(StringUtil.alphanumeric))

  /** Throws `IllegalArgumentException` unless `admits(name)`. */
  def of(name: String): VariableText =
    if StringUtil.isDecimalLiteral(name) then Decimal(DecimalText(name))
    else Word(WordText(name))

/**
 * The name of a predicate or function: the text of a `Token.Word`, or an operator.
 *
 * {{{
 * SymbolText.of("<")      // Operator(GrammarSymbol.Less)
 * SymbolText.of("large")  // Word(WordText("large"))
 * }}}
 */
enum SymbolText:
  case Word(text: WordText)
  case Decimal(text: DecimalText)
  case Operator(symbol: GrammarSymbol)

  def value: String = this match
    case Word(text)       => text.value
    case Decimal(text)    => text.value
    case Operator(symbol) => symbol.text

object SymbolText:

  /** Whether `name` is a word, a decimal or a `GrammarSymbol`'s text. */
  def admits(name: String): Boolean =
    GrammarSymbol.fromText(name).isDefined || VariableText.admits(name)

  /** Throws `IllegalArgumentException` unless `admits(name)`. */
  def of(name: String): SymbolText =
    GrammarSymbol.fromText(name) match
      case Some(symbol) => Operator(symbol)
      case None         =>
        VariableText.of(name) match
          case VariableText.Word(text)    => Word(text)
          case VariableText.Decimal(text) => Decimal(text)
