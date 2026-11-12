package parser

import lexer.{Found, Token}
import logic.Term
import logic.Term.*
import parser.Combinators.*
import util.StringUtil
import util.StringUtil.*

/**
 * Term parser from fol.ml
 *
 * Parses first-order logic terms with:
 *   - Variables: x, y, z
 *   - Constants: 42, nil, "IT Risk" (emitted as `Term.Const`; see [[isConstName]])
 *   - Functions: f(x, y), +(x, y)
 *   - 6 levels of infix operators with different associativity
 *
 * Element-type refinement note: parser arms now match on [[lexer.Token]] cases rather than raw
 * strings (ADR-007 C13). The 1:1 expansion preserves Harrison's algorithmic structure verbatim.
 *
 * Intentional deviations from Harrison OCaml are documented in [[isConstName]] and
 * [[parseAtomicTerm]].
 */
object TermParser:

  /**
   * Check if a `Word` token's payload is a constant name.
   *
   * OCaml implementation: let is_const_name s = forall numeric (explode s) or s = "nil"
   *
   * A constant is either:
   *   - All numeric digits (e.g., "42", "0", "10000000")
   *   - The special name "nil"
   *   - A decimal literal (e.g., "0.05", "3.14") — intentional deviation from Harrison OCaml;
   *     merged decimal tokens arrive as a single [[lexer.Token.Word]] via VagueQueryParser's
   *     `mergeDecimalTokens` post-processor.
   */
  private def isConstName(s: String): Boolean =
    s.forall(numeric) || s == "nil" || StringUtil.isDecimalLiteral(s)

  /**
   * Parse atomic term (highest precedence / base case)
   *
   * OCaml implementation:
   *   let rec parse_atomic_term vs inp =
   *     match inp with
   *       [] -> failwith "term expected"
   *     | "("::rest -> parse_bracketed (parse_term vs) ")" rest
   *     | "-"::rest -> papply (fun t -> Fn("-",[t])) (parse_atomic_term vs rest)
   *     | f::"("::")"::rest -> Fn(f,[]),rest
   *     | f::"("::rest ->
   *         papply (fun args -> Fn(f,args))
   *                (parse_bracketed (parse_list "," (parse_term vs)) ")" rest)
   *     | a::rest ->
   *         (if is_const_name a & not(mem a vs) then Fn(a,[]) else Var a),rest
   *
   * Scala extensions:
   *  - [[lexer.Token.StringLit]] (quoted literal, e.g. `"IT Risk"`) → always
   *    a [[Term.Const]] carrying the inner content, so a multi-word name is
   *    a constant and never a variable. The numeric / `nil` / decimal path
   *    through [[isConstName]] is unchanged.
   *  - Every term is built through [[Term.mkVar]], [[Term.mkFn]] and
   *    [[Term.mkConst]], where the OCaml source applies the constructors.
   */
  def parseAtomicTerm(vs: List[String])(inp: List[Token]): ParseResult[Term] =
    inp match
      case Nil =>
        // OCaml `failwith` → ParseFailure so callers (e.g. parseInfixAtom →
        // parseAtomicFormula) can backtrack to the bracketed-formula branch
        // for inputs like `~(~p)` where the leading `(` is not the start of a
        // term-level atom. ADR-007 C2: preserve OCaml error semantics.
        throw new ParseFailure(SyntaxFailure.TermExpected(Found.of(inp)))

      case Token.LParen :: rest =>
        // Parenthesized term
        parseBracketed(parseTerm(vs), Token.RParen)(rest)

      case Token.OpSym("-") :: rest =>
        // Unary minus
        papply((t: Term) => Term.mkFn("-", List(t)))(parseAtomicTerm(vs)(rest))

      case Token.StringLit(content) :: rest =>
        // Quoted literal — always a constant whose value is
        // the inner content. The lexer has already stripped surrounding quotes.
        (Term.mkConst(content), rest)

      case Token.Word(f) :: Token.LParen :: Token.RParen :: rest =>
        // Function with no arguments: f()
        (Term.mkFn(f, List()), rest)

      case Token.Word(f) :: Token.LParen :: rest =>
        // Function with arguments: f(arg1, arg2, ...)
        papply((args: List[Term]) => Term.mkFn(f, args))(
          parseBracketed(
            parseList(Token.Comma)(parseTerm(vs)),
            Token.RParen,
          )(rest)
        )

      case Token.Word(a) :: rest =>
        // Variable or constant.
        // It's a constant if: all numeric OR "nil" OR decimal literal AND not
        // in bound variables.
        // NOTE: intentional deviation from Harrison OCaml — OCaml emits Fn(a,[])
        // for zero-arity constants because its `term` type has no Const variant.
        // This Scala port adds Term.Const precisely so QueryBinder can distinguish
        // inline literals from zero-arity function applications at the bind phase.
        if isConstName(a) && !vs.contains(a) then (Term.mkConst(a), rest)
        else (Term.mkVar(a), rest)

      case _ :: _ =>
        // Token cannot start a term — backtrackable failure (see Nil arm note).
        throw new ParseFailure(SyntaxFailure.TermExpected(Found.of(inp)))

  /**
   * Parse term with all infix operators.
   *
   * SIX LEVELS OF OPERATORS (lowest to highest precedence):
   * 1. ::  (cons, list constructor)    - RIGHT associative
   * 2. +   (addition)                  - RIGHT associative
   * 3. -   (subtraction)               - LEFT associative
   * 4. *   (multiplication)            - RIGHT associative
   * 5. /   (division)                  - LEFT associative
   * 6. ^   (exponentiation)            - LEFT associative
   */
  def parseTerm(vs: List[String])(inp: List[Token]): ParseResult[Term] =
    parseRightInfix(Token.OpSym("::"), (e1: Term, e2: Term) => Term.mkFn("::", List(e1, e2)))(
      parseRightInfix(Token.OpSym("+"), (e1: Term, e2: Term) => Term.mkFn("+", List(e1, e2)))(
        parseLeftInfix(Token.OpSym("-"), (e1: Term, e2: Term) => Term.mkFn("-", List(e1, e2)))(
          parseRightInfix(Token.OpSym("*"), (e1: Term, e2: Term) => Term.mkFn("*", List(e1, e2)))(
            parseLeftInfix(Token.OpSym("/"), (e1: Term, e2: Term) => Term.mkFn("/", List(e1, e2)))(
              parseLeftInfix(Token.OpSym("^"), (e1: Term, e2: Term) => Term.mkFn("^", List(e1, e2)))(
                parseAtomicTerm(vs)
              )
            )
          )
        )
      )
    )(inp)

  /** Convenience wrapper: parse term from token list with empty variable context. */
  def parse(tokens: List[Token]): ParseResult[Term] =
    parseTerm(List())(tokens)

  /** Parse term from string (with lexing and exhaustion checking) */
  def parseFromString(s: String): Term =
    import lexer.Lexer.lex
    val tokens         = lex(explode(s))
    val (result, rest) = parse(tokens)
    if rest.isEmpty then result
    else throw new Exception(s"Unparsed input: ${tokensLabel(rest)}")

end TermParser
