package parser

/** Where in the text handed to a parser a refusal points. */
enum SourcePosition:
  /** A UTF-16 code-unit offset from zero, within the input. */
  case At(offset: Int)
  case Unknown
