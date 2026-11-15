package lexer

import util.StringUtil.*

import scala.annotation.tailrec

/**
 * Signals an unrecoverable lexer error in a `"…"`-delimited string literal.
 *
 * `remaining` counts the characters from the refused one to the end of the input, and is 0 when
 * the input ended inside the literal. A boundary turns it into an offset as `s.length - remaining`.
 * Mirrors the exception-based error idiom of the OCaml-ported parser combinators (ADR-002, ADR-007
 * C2, ADR-012 §"Relationship to ADR-007"). Distinct from `Combinators.ParseFailure` so a boundary
 * maps it without conflating it with parser-level backtracking.
 */
class LexerError(val failure: LexFailure, val remaining: Int) extends Exception(failure.message)

/**
 * Lexical analysis following OCaml style from intro.ml
 *
 * Converts character streams into [[Token]] streams. The OCaml original recurses per token; this
 * port loops with an accumulator, so its stack use is constant in the input's length (ADR-024).
 *
 * Element-type refinement `string` ⇒ [[Token]] is permitted under ADR-007 C13: combinator shape
 * preserved (signature arity `List[Char] => List[Token]`, exception backtracking); each `Token`
 * case documents the OCaml `string` it replaces (see [[Token]]); downstream parser pattern matches
 * expand 1:1 from string-match arms to ADT-match arms.
 */
object Lexer:

  /**
   * Consume characters while predicate holds (OCaml: lexwhile)
   *
   * OCaml implementation:
   *   let rec lexwhile prop inp =
   *     match inp with
   *       c::cs when prop c -> let tok,rest = lexwhile prop cs in c^tok,rest
   *     | _ -> "",inp
   *
   * Operates on `(String, List[Char])` regardless of the outer `lex` return type. The Scala body is
   * iterative, so the stack use is constant in the input's length (ADR-024).
   */
  def lexwhile(prop: Char => Boolean, inp: List[Char]): (String, List[Char]) =
    val (run, rest) = inp.span(prop)
    (run.mkString, rest)

  /**
   * Single-character punctuation alphabet (closed, finite).
   *
   * Each char in this set maps to a dedicated [[Token]] case via [[punctuationToken]]. It keeps `.`
   * and `,` out of the multi-char symbolic runs that `StringUtil.symbolic` would place them in.
   *
   * `;` stays out: no grammar arm reads it, and it lexes as a symbolic-run token
   * (`Token.OpSym(";")`).
   */
  private val punctChars: Set[Char] = Set('(', ')', '[', ']', '{', '}', ',', '.')

  private def punctuationToken(c: Char): Token = c match
    case '(' => Token.LParen
    case ')' => Token.RParen
    case '[' => Token.LBracket
    case ']' => Token.RBracket
    case '{' => Token.LBrace
    case '}' => Token.RBrace
    case ',' => Token.Comma
    case '.' => Token.Dot

  /**
   * The characters a quoted literal admits: letters, digits and `_` as in a bare word without the
   * apostrophe, the space, `/` and `-` of consumer node names, and the `.`, `+` and `-` of a
   * number.
   */
  private[lexer] val literalChars: Set[Char] =
    ("abcdefghijklmnopqrstuvwxyz" + "ABCDEFGHIJKLMNOPQRSTUVWXYZ" + "0123456789" + " /_.+-").toSet

  /**
   * Consume a `"…"`-delimited string literal up to (but not including) the closing quote. No
   * backslash escaping. A missing closing quote, an embedded newline and a character outside
   * [[literalChars]] each raise [[LexerError]].
   *
   * Caller has already consumed the opening `"`. Returns `(content, rest)` where `rest` begins
   * immediately after the closing `"`. The body is iterative, so the stack use is constant in the
   * literal's length (ADR-024).
   */
  private def lexStringLit(inp: List[Char]): (String, List[Char]) =
    @tailrec
    def loop(rest: List[Char], acc: List[Char]): (String, List[Char]) =
      rest match
        case Nil                                 =>
          throw new LexerError(LexFailure.UnterminatedLiteral, 0)
        case '\n' :: _                           =>
          throw new LexerError(LexFailure.NewlineInLiteral, rest.length)
        case '"' :: after                        =>
          (acc.reverse.mkString, after)
        case c :: _ if !literalChars.contains(c) =>
          throw new LexerError(LexFailure.RefusedLiteralCharacter, rest.length)
        case c :: cs                             =>
          loop(cs, c :: acc)
    loop(inp, Nil)

  /**
   * Main lexer function (OCaml: lex)
   *
   * OCaml implementation:
   *   let rec lex inp =
   *     match snd(lexwhile space inp) with
   *       [] -> []
   *     | c::cs -> let prop = if alphanumeric(c) then alphanumeric
   *                           else if symbolic(c) then symbolic
   *                           else fun c -> false in
   *                let toktl,rest = lexwhile prop cs in
   *                (c^toktl)::lex rest
   *
   * Scala extensions over the OCaml original (admissible under ADR-007 C13):
   *  - punctuation chars in [[punctChars]] short-circuit to dedicated [[Token]]
   *    cases before the symbolic check (resolves the `.`/`,` overlap noted in
   *    `StringUtil.symbolic`);
   *  - `"` opens a [[Token.StringLit]] branch consuming until the matching `"`
   *    (no escapes; a newline or a character outside the literal alphabet ⇒
   *    [[LexerError]]).
   *
   * The Scala body is iterative, so the stack use is constant in the input's length (ADR-024).
   */
  def lex(inp: List[Char]): List[Token] =
    @tailrec
    def loop(inp: List[Char], acc: List[Token]): List[Token] =
      // Skip whitespace: snd(lexwhile space inp)
      val (_, afterSpace) = lexwhile(space, inp)

      afterSpace match
        case Nil => acc.reverse

        case '"' :: cs =>
          // String-literal branch (no OCaml counterpart).
          val (content, rest) = lexStringLit(cs)
          loop(rest, Token.StringLit(content) :: acc)

        case c :: cs if punctChars.contains(c) =>
          // Single-character punctuation — emit dedicated Token case.
          loop(cs, punctuationToken(c) :: acc)

        case c :: cs =>
          // OCaml original: alphanumeric run, symbolic run, or single-char fallthrough.
          if alphanumeric(c) then
            val (toktl, rest) = lexwhile(alphanumeric, cs)
            loop(rest, Token.Word(c.toString + toktl) :: acc)
          else if symbolic(c) then
            val (toktl, rest) = lexwhile(symbolic, cs)
            loop(rest, Token.OpSym(c.toString + toktl) :: acc)
          else
            // Any other character — emit as a single-char OpSym to preserve the
            // OCaml "permissive" fallthrough for unexpected input. Reachable only
            // for chars outside alphanumeric ∪ symbolic ∪ punctuation ∪ space ∪ {'"'}.
            loop(cs, Token.OpSym(c.toString) :: acc)
      end match
    end loop

    loop(inp, Nil)

  end lex

  /** Convenience function: lex from string directly */
  def lexString(s: String): List[Token] =
    lex(explode(s))

end Lexer
