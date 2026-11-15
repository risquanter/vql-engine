package parser

import lexer.Token

import scala.annotation.tailrec

/** Why `NestingBound.check` refused a token list before parsing. */
enum NestingFailure:
  /** Parentheses nest deeper than `limit`. */
  case ParenthesesTooDeep(limit: Int)

  /** More than `limit` operator tokens and quantified variables. Each can add one level to the parsed tree. */
  case TooManyOperators(limit: Int)

  def message: String = this match
    case ParenthesesTooDeep(limit) => s"Parentheses nest deeper than $limit"
    case TooManyOperators(limit)   => s"The formula holds more than $limit operators and quantified variables"

/**
 * A bound on the stack a parse and every walk of its tree can use, checked on the tokens before the
 * parser runs (ADR-024).
 *
 * The tree's depth is at most the operator tokens, plus the quantified variables, plus the
 * parenthesis depth, plus 3, so the two limits bound every recursion over the tokens and the tree.
 */
object NestingBound:

  /** Half the smallest parenthesis depth measured to overflow a 1 MiB interpreted JVM thread, rounded down to a power of two. */
  val MaxParenthesisDepth: Int = 64

  /** Keeps a tree within the limits under half the smallest tree depth measured to overflow evaluation on Node. */
  val MaxOperators: Int = 256

  /**
   * `Left` at the first token past either limit, `Right` otherwise. Unbalanced parentheses are left
   * to the parser to refuse.
   *
   * {{{
   * check(lex("((p))"))                                   // Right(())
   * check(lex("(" * 65 + "p" + ")" * 65))                 // Left(ParenthesesTooDeep(64))
   * check(lex(List.fill(258)("p").mkString(" \\/ ")))     // Left(TooManyOperators(256))
   * }}}
   */
  def check(tokens: List[Token]): Either[NestingFailure, Unit] =
    @tailrec
    def loop(rest: List[Token], depth: Int, operators: Int, inVariableList: Boolean): Either[NestingFailure, Unit] =
      rest match
        case Nil           => Right(())
        case token :: more =>
          val (d, ops, inList) = step(token, depth, operators, inVariableList)
          if d > MaxParenthesisDepth then Left(NestingFailure.ParenthesesTooDeep(MaxParenthesisDepth))
          else if ops > MaxOperators then Left(NestingFailure.TooManyOperators(MaxOperators))
          else loop(more, d, ops, inList)
    loop(tokens, 0, 0, false)

  /** The counts after one token. A quantifier's variable list runs from `forall` or `exists` to the next non-word token. */
  private def step(token: Token, depth: Int, operators: Int, inVariableList: Boolean): (Int, Int, Boolean) =
    (token, inVariableList) match
      case (Token.LParen, _)                    => (depth + 1, operators, false)
      case (Token.RParen, _)                    => (depth - 1, operators, false)
      case (Token.OpSym(_), _)                  => (depth, operators + 1, false)
      case (Token.Word("forall" | "exists"), _) => (depth, operators, true)
      case (Token.Word(_), true)                => (depth, operators + 1, true)
      case _                                    => (depth, operators, false)

end NestingBound
