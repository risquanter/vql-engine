package vql.parser

import lexer.{Found, GrammarSymbol, Lexer, LexerError, Token, TokenKind, WordText}
import parser.{FOLAtomParser, FormulaParser, SourcePosition}
import parser.Combinators.{ParseFailure, SyntaxError, tokenLabel}
import util.StringUtil
import vql.error.{ParseContext, ParseRefusal, QueryError, QueryException, QuantifierPart}
import vql.logic.ParsedQuery
import vql.quantifier.Quantifier

/**
 * Parser for vague queries (paper Section 5.2)
 *
 * Syntax: Q[op]^{k/n} x (R(x,y'), φ(x,y))(y₁, ..., yₘ)
 *
 * The range `R(x,y')` is a full FOL formula (ADR-017), so it may be a single atom or a compound of
 * `/\`, `\/`, `~`, and inner quantifiers.
 *
 * Examples: Q[>=]^{3/4} x (country(x), exists y (hasGDP_agr(x,y) /\ y<=20)) Q[~]^{1/2} x
 * (capital(x, y), large(x))(y) Q[<=]^{1/3} x (server(x) /\ ~patched(x), exploitable(x))
 *
 * OCaml-style: parsers are `List[Token] => (A, List[Token])` (ADR-007 C13). Exceptions signal parse
 * failure — mirrors OCaml's `try … with Failure _ ->` backtracking pattern. The single Either
 * boundary lives at the public `parse` entry point.
 *
 * OCaml reference: parsing pattern from fol.ml / formulas.ml Paper reference: Definition 1 (Section
 * 5.2)
 */
object VagueQueryParser:

  // ── Public API ──────────────────────────────────────────────────────

  /**
   * Parse a vague query string.
   *
   * `Left(QueryError.ParseError)` when the text is no query, and `Left(QueryError.ValidationError)`
   * when a parsed query breaks a range rule. An exception that is neither the parsers' own failure
   * nor a `QueryException` propagates.
   */
  def parse(s: String): Either[QueryError, ParsedQuery] =
    try
      val tokens             = mergeDecimalTokens(Lexer.lex(StringUtil.explode(s)))
      val (query, remaining) = parseTokens(tokens)
      remaining match
        case Nil           => Right(query)
        case first :: more =>
          Left(refused(ParseRefusal.TrailingTokens(TokenKind.of(first), more.map(TokenKind.of))))
    catch
      case e: QueryException =>
        Left(e.error)
      case e: LexerError     =>
        Left(QueryError.ParseError(ParseRefusal.Lexical(e.failure), SourcePosition.At(s.length - e.remaining)))
      case e: ParseFailure   =>
        Left(refused(ParseRefusal.Formula(e.failure)))
      case e: SyntaxError    =>
        Left(refused(ParseRefusal.Formula(e.failure)))

  private def refused(refusal: ParseRefusal): QueryError.ParseError =
    QueryError.ParseError(refusal, SourcePosition.Unknown)

  /** The tokens of `Q [ op ] ^ {` and the numerator. */
  private val quantifierPrefixLength: Int = 7

  // ── Combinators ─────────────────────────────────────────────────────
  // Each is List[Token] => (A, List[Token]) — the ParseResult pattern.
  // Throws QueryException on error, like FOLAtomParser.

  /** Parse vague query from tokens — combinator style. */
  def parseTokens(tokens: List[Token]): (ParsedQuery, List[Token]) =
    // 1. Q[op]^{k/n}
    val (quantifier, t1) = parseQuantifier(tokens)
    // 2. x
    val (variable, t2)   = parseVariable(t1)
    // 3. (
    val t3               = expect(GrammarSymbol.LParen, t2, ParseContext.RangeFormula)
    // 4. R(x,y') range formula — delegate to the formula parser (OCaml style).
    //    `,` is not an infix operator, so parsing stops at the range/scope comma.
    val (range, t4)      = FormulaParser.parse(
      FOLAtomParser.parseInfixAtom,
      FOLAtomParser.parseAtom,
    )(t3)
    // 5. ,
    val t5               = expect(GrammarSymbol.Comma, t4, ParseContext.ScopeFormula)
    // 6. φ(x,y) — delegate to formula parser (OCaml style)
    val (scope, t6)      = FormulaParser.parse(
      FOLAtomParser.parseInfixAtom,
      FOLAtomParser.parseAtom,
    )(t5)
    // 7. )
    val t7               = expect(GrammarSymbol.RParen, t6, ParseContext.AnswerVariables)
    // 8. Optional answer variables (y₁, …, yₘ)
    val (answerVars, t8) = parseAnswerVars(t7)
    // 9. Construct & validate
    val query            = ParsedQuery.mk(quantifier, variable, range, scope, answerVars)
    (query, t8)

  end parseTokens

  /**
   * Parse quantifier: Q[op]^{k/n}
   *
   * Syntax: Q[~]^{k/n} — About Q[>=]^{k/n} — AtLeast Q[<=]^{k/n} — AtMost
   *
   * Optional tolerance: Q[~]^{k/n}[ε]
   *
   * The bracketed operator may arrive as a single [[Token.OpSym]] (e.g. `>=`, `<=`, `~`) or as a
   * [[Token.Word]] when an operator happens to be alphanumeric. Both shapes are accepted via
   * [[opLabel]].
   */
  private def parseQuantifier(tokens: List[Token]): (Quantifier, List[Token]) =
    tokens match
      case Token.Word("Q") :: Token.LBracket :: opTok :: Token.RBracket ::
        Token.OpSym("^") :: Token.LBrace :: rest =>
        val op          = opLabel(opTok)
        val (k, afterK) = parseInteger(rest, QuantifierPart.Numerator)
        val afterSlash  = expect(GrammarSymbol.Divide, afterK, ParseContext.Denominator)
        val (n, afterN) = parseInteger(afterSlash, QuantifierPart.Denominator)
        val afterBrace  = expect(GrammarSymbol.RBrace, afterN, ParseContext.ToleranceOrEnd)

        // Optional tolerance [ε]
        val (tolerance, afterTol) = afterBrace match
          case Token.LBracket :: Token.Word(tolStr) :: Token.RBracket :: rest2
            if isNumeric(tolStr) =>
            (tolStr.toDouble, rest2)
          case _ =>
            (0.1, afterBrace) // Default tolerance

        val build: (Int, Int, Double) => Quantifier = op match
          case "~" | "~#" => Quantifier.mkAbout
          case ">=" | "≥" => Quantifier.mkAtLeast
          case "<=" | "≤" => Quantifier.mkAtMost
          case _          =>
            throw QueryException(refused(ParseRefusal.UnknownQuantifierOperator(TokenKind.of(opTok))))

        val quantifier = checkedQuantifier(k, n, tolerance, build)

        (quantifier, afterTol)

      case _ =>
        throw QueryException(
          refused(ParseRefusal.QuantifierExpected(tokens.take(quantifierPrefixLength).map(TokenKind.of)))
        )

  /**
   * Builds the quantifier once its threshold `k/n` is a proportion and its tolerance lies in
   * `[0, 1]`, and throws the typed refusal otherwise. `build` checks the same bounds again.
   */
  private def checkedQuantifier(
    k: Int,
    n: Int,
    tolerance: Double,
    build: (Int, Int, Double) => Quantifier,
  ): Quantifier =
    if n <= 0 || k < 0 || k > n then
      throw QueryException(refused(ParseRefusal.FractionOutOfRange(k, n)))
    else if tolerance < 0 || tolerance > 1 then
      throw QueryException(refused(ParseRefusal.ToleranceOutOfRange(tolerance)))
    else build(k, n, tolerance)

  /** Parse variable name: single identifier token. */
  private def parseVariable(tokens: List[Token]): (String, List[Token]) =
    tokens match
      case Token.Word(v) :: rest if isIdentifier(v) => (v, rest)
      case _                                        =>
        throw QueryException(refused(ParseRefusal.VariableExpected(Found.of(tokens))))

  /** Parse optional answer variables: (y₁, …, yₘ) */
  private def parseAnswerVars(tokens: List[Token]): (List[String], List[Token]) =
    tokens match
      case Token.LParen :: rest =>
        val (vars, afterVars) = parseVariableList(rest)
        val afterClose        = expect(GrammarSymbol.RParen, afterVars, ParseContext.EndOfAnswerVariables)
        (vars, afterClose)
      case _                    =>
        (Nil, tokens) // No answer variables (Boolean query)

  /** Parse comma-separated list of variables. */
  private def parseVariableList(tokens: List[Token]): (List[String], List[Token]) =
    def loop(tokens: List[Token], acc: List[String]): (List[String], List[Token]) =
      tokens match
        case Token.Word(v) :: Token.Comma :: rest if isIdentifier(v) =>
          loop(rest, acc :+ v)
        case Token.Word(v) :: rest if isIdentifier(v)                =>
          (acc :+ v, rest)
        case Token.RParen :: _                                       =>
          (acc, tokens) // Empty list or end of list
        case _                                                       =>
          throw QueryException(refused(ParseRefusal.AnswerVariableExpected(Found.of(tokens))))
    loop(tokens, Nil)

  end parseVariableList

  // ── Helpers (OCaml-style) ───────────────────────────────────────────

  /** Expect a specific token — OCaml pattern from fol.ml. */
  private def expect(expected: GrammarSymbol, tokens: List[Token], before: ParseContext): List[Token] =
    tokens match
      case head :: rest if TokenKind.of(head) == TokenKind.Symbol(expected) => rest
      case _                                                                =>
        throw QueryException(refused(ParseRefusal.SymbolExpected(expected, before, Found.of(tokens))))

  /** Parse integer token (a `Word` whose payload is all digits). */
  private def parseInteger(tokens: List[Token], part: QuantifierPart): (Int, List[Token]) =
    tokens match
      case Token.Word(num) :: rest if num.nonEmpty && num.forall(_.isDigit) =>
        try (num.toInt, rest)
        catch
          case _: NumberFormatException =>
            throw QueryException(refused(ParseRefusal.IntegerOutOfRange(part, WordText(num))))
      case _                                                                =>
        throw QueryException(refused(ParseRefusal.IntegerExpected(part, Found.of(tokens))))

  /**
   * Render the operator slot inside `Q[…]` as a string for downstream matching.
   *
   * The bracketed operator may lex as a [[Token.Word]] (`~`, but `~` is in `symbolic`, so this is
   * rare — kept defensively), a [[Token.OpSym]] (`>=`, `<=`, `~`, `~#`), or a single non-Western
   * glyph wrapped as [[Token.OpSym]] (e.g. `≥`, `≤`).
   */
  private def opLabel(t: Token): String = t match
    case Token.Word(s)  => s
    case Token.OpSym(s) => s
    case other          => tokenLabel(other)

  /** Check if string is a valid identifier (alphanumeric, starts with letter). */
  private def isIdentifier(s: String): Boolean =
    s.nonEmpty && s.head.isLetter && s.forall(c => c.isLetterOrDigit || c == '_')

  /** Check if string is numeric (integer or decimal). */
  private def isNumeric(s: String): Boolean =
    StringUtil.isNumeric(s) || StringUtil.isDecimalLiteral(s)

  /**
   * Merge `Word(digits)` `Dot` `Word(digits)` triples into a single `Word("d1.d2")` decimal token.
   *
   * The lexer treats `.` as punctuation (matching the OCaml source), so a decimal literal such as
   * `0.05` lexes as `[Token.Word("0"), Token.Dot, Token.Word("05")]`; this post-processor collapses
   * such triples to `[Token.Word("0.05")]` so [[TermParser]]'s `isConstName` recognises the merged
   * form.
   *
   * Only digit-dot-digit triples are merged; other token sequences are unchanged.
   */
  private def mergeDecimalTokens(tokens: List[Token]): List[Token] =
    tokens match
      case Token.Word(a) :: Token.Dot :: Token.Word(b) :: rest
        if a.nonEmpty && a.forall(_.isDigit) && b.nonEmpty && b.forall(_.isDigit) =>
        Token.Word(s"$a.$b") :: mergeDecimalTokens(rest)
      case t :: rest => t :: mergeDecimalTokens(rest)
      case Nil       => Nil

end VagueQueryParser
