package vql.parser

import lexer.{Found, GrammarSymbol, LexFailure, SymbolText, TokenKind, VariableText, WordText}
import logic.{FOL, FOLUtil, Formula, Term}
import parser.{SourcePosition, SyntaxFailure}
import vql.error.{ParseContext, ParseRefusal, QueryError, QuantifierPart, TypeCheckError}
import vql.logic.ParsedQuery
import vql.quantifier.Quantifier

class VagueQueryParserSpec extends munit.FunSuite:
  import VagueQueryParser.*

  // Helper: extract Right or fail test
  def parseOk(input: String): ParsedQuery =
    parse(input) match
      case Right(q) => q
      case Left(e)  => fail(s"Parse failed: ${e.formatted}")

  // Helper: a single-atom range is Formula.Atom(fol) — extract the atom or fail.
  def rangeAtom(q: ParsedQuery): FOL =
    q.range match
      case Formula.Atom(fol) => fol
      case other             => fail(s"Expected atom range, got: $other")

  // Helper to check query structure
  def assertQueryStructure(
    query: ParsedQuery,
    expectedVar: String,
    expectedRangePred: String,
    expectedAnswerVars: List[String],
  ): Unit =
    assertEquals(query.variable, expectedVar)
    assertEquals(rangeAtom(query).predicate, expectedRangePred)
    assertEquals(query.answerVars, expectedAnswerVars)

  // ==================== Basic Parsing Tests ====================

  test("parse simple Boolean query with About quantifier") {
    val q = parseOk("Q[~]^{1/2} x (country(x), large(x))")

    assertQueryStructure(q, "x", "country", Nil)
    q.quantifier match
      case Quantifier.About(k, n, tol) =>
        assertEquals(k, 1)
        assertEquals(n, 2)
        assertEquals(tol, 0.1) // Default tolerance
      case _ => fail("Expected About quantifier")

    q.scope match
      case Formula.Atom(fol) => assertEquals(fol.predicate, "large")
      case _                 => fail("Expected simple atom scope")
  }

  test("parse query with AtLeast quantifier") {
    val q = parseOk("Q[>=]^{3/4} x (country(x), large(x))")

    q.quantifier match
      case Quantifier.AtLeast(k, n, _) =>
        assertEquals(k, 3)
        assertEquals(n, 4)
      case _                           => fail("Expected AtLeast quantifier")
  }

  test("parse query with AtMost quantifier") {
    val q = parseOk("Q[<=]^{1/3} x (city(x), populous(x))")

    q.quantifier match
      case Quantifier.AtMost(k, n, _) =>
        assertEquals(k, 1)
        assertEquals(n, 3)
      case _                          => fail("Expected AtMost quantifier")
  }

  test("parse query with custom tolerance") {
    val q = parseOk("Q[~]^{1/2}[0.05] x (country(x), large(x))")

    q.quantifier match
      case Quantifier.About(k, n, tol) =>
        assertEquals(tol, 0.05)
      case _                           => fail("Expected About quantifier")
  }

  test("parse query with single answer variable") {
    val q = parseOk("Q[~]^{1/2} x (capital(x, y), large(x))(y)")

    assertQueryStructure(q, "x", "capital", List("y"))

    // Verify range has two terms
    assertEquals(rangeAtom(q).terms.length, 2)
  }

  test("parse query with multiple answer variables") {
    val q = parseOk("Q[>=]^{2/3} x (borders(x, y, z), conflict(x))(y, z)")

    assertQueryStructure(q, "x", "borders", List("y", "z"))
    assertEquals(rangeAtom(q).terms.length, 3)
  }

  // ==================== Complex Formula Parsing ====================

  test("parse query with conjunction in scope") {
    val q = parseOk("Q[~]^{1/2} x (country(x), large(x) /\\ coastal(x))")

    q.scope match
      case Formula.And(_, _) => // Success
      case _                 => fail("Expected conjunction in scope")
  }

  test("parse query with disjunction in scope") {
    val q = parseOk("Q[>=]^{3/4} x (country(x), large(x) \\/ populous(x))")

    q.scope match
      case Formula.Or(_, _) => // Success
      case _                => fail("Expected disjunction in scope")
  }

  test("parse query with negation in scope") {
    val q = parseOk("Q[<=]^{1/4} x (country(x), ~large(x))")

    q.scope match
      case Formula.Not(_) => // Success
      case _              => fail("Expected negation in scope")
  }

  test("parse query with existential quantifier in scope") {
    val q = parseOk("Q[>=]^{3/4} x (country(x), exists y . (hasGDP(x, y) /\\ y < 20))")

    q.scope match
      case Formula.Exists(y, _) =>
        assertEquals(y, "y")
      case _                    => fail("Expected existential quantifier in scope")
  }

  test("parse query with universal quantifier in scope") {
    val q = parseOk("Q[~]^{1/2} x (country(x), forall y . (city(y) ==> inCountry(y, x)))")

    q.scope match
      case Formula.Forall(y, _) =>
        assertEquals(y, "y")
      case _                    => fail("Expected universal quantifier in scope")
  }

  test("parse query with implication in scope") {
    val q = parseOk("Q[>=]^{2/3} x (country(x), large(x) ==> wealthy(x))")

    q.scope match
      case Formula.Imp(_, _) => // Success
      case _                 => fail("Expected implication in scope")
  }

  test("parse query with biconditional in scope") {
    val q = parseOk("Q[~]^{1/2} x (country(x), large(x) <=> populous(x))")

    q.scope match
      case Formula.Iff(_, _) => // Success
      case _                 => fail("Expected biconditional in scope")
  }

  // ==================== Paper Examples ====================

  test("paper example q1: Boolean query with existential") {
    val q = parseOk("""Q[>=]^{3/4} x (country(x), exists y . (hasGDP_agr(x, y) /\ y <= 20))""")

    assertQueryStructure(q, "x", "country", Nil)
    assert(q.isBoolean)

    q.quantifier match
      case Quantifier.AtLeast(k, n, _) =>
        assertEquals(k, 3)
        assertEquals(n, 4)
      case _                           => fail("Expected AtLeast quantifier")

    q.scope match
      case Formula.Exists(y, Formula.And(_, _)) =>
        assertEquals(y, "y")
      case _                                    => fail("Expected exists y (... /\\ ...)")
  }

  test("paper example q3 skeleton: unary query with answer variable") {
    val q = parseOk("Q[~]^{1/2} x (capital(x, y), large(x))(y)")

    assertQueryStructure(q, "x", "capital", List("y"))
    assert(q.isUnary)

    q.quantifier match
      case Quantifier.About(k, n, _) =>
        assertEquals(k, 1)
        assertEquals(n, 2)
      case _                         => fail("Expected About quantifier")
  }

  // ==================== Alternative Operator Syntax ====================

  test("parse with ~# operator (alternative About syntax)") {
    val q = parseOk("Q[~#]^{1/2} x (country(x), large(x))")

    q.quantifier match
      case Quantifier.About(_, _, _) => // Success
      case _                         => fail("Expected About quantifier")
  }

  test("parse with Unicode >= operator") {
    val q = parseOk("Q[≥]^{3/4} x (country(x), large(x))")

    q.quantifier match
      case Quantifier.AtLeast(_, _, _) => // Success
      case _                           => fail("Expected AtLeast quantifier")
  }

  test("parse with Unicode <= operator") {
    val q = parseOk("Q[≤]^{1/4} x (country(x), large(x))")

    q.quantifier match
      case Quantifier.AtMost(_, _, _) => // Success
      case _                          => fail("Expected AtMost quantifier")
  }

  // ==================== Edge Cases and Validation ====================

  test("parse query with True scope") {
    val q = parseOk("Q[~]^{1/2} x (country(x), true)")

    q.scope match
      case Formula.True => // Success
      case _            => fail("Expected True formula")
  }

  test("parse query with False scope") {
    val q = parseOk("Q[~]^{1/2} x (country(x), false)")

    q.scope match
      case Formula.False => // Success
      case _             => fail("Expected False formula")
  }

  test("parse rejects invalid quantifier operator") {
    assert(parse("Q[*]^{1/2} x (country(x), large(x))").isLeft)
  }

  test("parse rejects missing quantifier") {
    assert(parse("x (country(x), large(x))").isLeft)
  }

  test("parse rejects missing variable") {
    assert(parse("Q[~]^{1/2} (country(x), large(x))").isLeft)
  }

  test("parse rejects missing range") {
    assert(parse("Q[~]^{1/2} x (, large(x))").isLeft)
  }

  test("parse rejects missing scope") {
    assert(parse("Q[~]^{1/2} x (country(x), )").isLeft)
  }

  test("parse rejects unmatched parentheses") {
    assert(parse("Q[~]^{1/2} x (country(x), large(x)").isLeft)
  }

  test("parse rejects extra tokens after query") {
    assert(parse("Q[~]^{1/2} x (country(x), large(x)) extra").isLeft)
  }

  // ==================== Query Validation ====================

  test("parser validates quantified variable in range") {
    // This should fail validation in ParsedQuery.mk
    assert(parse("Q[~]^{1/2} x (country(y), large(x))").isLeft)
  }

  test("parser validates range variables subset of answer vars") {
    // y appears in range but not in answer variables
    assert(parse("Q[~]^{1/2} x (capital(x, y), large(x))").isLeft)
  }

  test("parser accepts when range vars are answer vars") {
    // y appears in range and in answer variables - should succeed
    val q = parseOk("Q[~]^{1/2} x (capital(x, y), large(x))(y)")
    assertQueryStructure(q, "x", "capital", List("y"))
  }

  // ==================== Formula Ranges (ADR-017) ====================

  test("parse negation range ~p(x)") {
    val q = parseOk("Q[~]^{1/2} x (~country(x), large(x))")
    q.range match
      case Formula.Not(Formula.Atom(fol)) => assertEquals(fol.predicate, "country")
      case other                          => fail(s"Expected Not(Atom), got: $other")
  }

  test("parse conjunction range p(x) /\\ q(x)") {
    val q = parseOk("Q[~]^{1/2} x (country(x) /\\ coastal(x), large(x))")
    q.range match
      case Formula.And(Formula.Atom(a), Formula.Atom(b)) =>
        assertEquals(a.predicate, "country")
        assertEquals(b.predicate, "coastal")
      case other => fail(s"Expected And(Atom, Atom), got: $other")
    // The formula parser stops at the range/scope comma — scope is the atom after it.
    q.scope match
      case Formula.Atom(fol) => assertEquals(fol.predicate, "large")
      case other             => fail(s"Expected atom scope, got: $other")
  }

  test("parse disjunction range p(x) \\/ q(x)") {
    val q = parseOk("Q[>=]^{3/4} x (country(x) \\/ coastal(x), large(x))")
    q.range match
      case Formula.Or(Formula.Atom(_), Formula.Atom(_)) => // Success
      case other => fail(s"Expected Or(Atom, Atom), got: $other")
  }

  test("parse existential range exists a . r(x, a)") {
    val q = parseOk("Q[~]^{1/2} x (exists a . r(x, a), large(x))")
    q.range match
      case Formula.Exists("a", Formula.Atom(fol)) => assertEquals(fol.predicate, "r")
      case other                                  => fail(s"Expected Exists(a, Atom), got: $other")
  }

  test("parse parenthesized compound range") {
    val q = parseOk("Q[>=]^{2/3} x ((country(x) /\\ coastal(x)) \\/ populous(x), large(x))")
    q.range match
      case Formula.Or(Formula.And(_, _), Formula.Atom(_)) => // Success
      case other => fail(s"Expected Or(And, Atom), got: $other")
  }

  test("parse infix-comparison atom range") {
    val q = parseOk("Q[~]^{1/2} x (x <= 20, large(x))")
    q.range match
      case Formula.Atom(fol) => assertEquals(fol.predicate, "<=")
      case other             => fail(s"Expected infix atom range, got: $other")
  }

  test("parse rejects range binding the quantified variable (not free)") {
    // `exists x . p(x)` binds x, so x is not free in the range → validation error.
    assert(parse("Q[~]^{1/2} x (exists x . country(x), large(x))").isLeft)
  }

  test("parse rejects range omitting the quantified variable") {
    // x must occur free in the range; y is an answer var (rule 2 satisfied), isolating rule 1.
    assert(parse("Q[~]^{1/2} x (country(y), large(x))(y)").isLeft)
  }

  test("parse rejects free range variable not among answer variables") {
    assert(parse("Q[~]^{1/2} x (city_of(x, z), large(x))").isLeft)
  }

  // ==================== Complex Nested Formulas ====================

  test("parse deeply nested formula") {
    val q = parseOk("""Q[>=]^{2/3} x (country(x), 
                     (large(x) /\ coastal(x)) \/ 
                     (populous(x) /\ wealthy(x)))""")

    q.scope match
      case Formula.Or(Formula.And(_, _), Formula.And(_, _)) => // Success
      case _ => fail("Expected complex nested formula")
  }

  test("parse query with multiple quantifiers in scope") {
    val q = parseOk("""Q[~]^{1/2} x (country(x), 
                     forall y . (city(y) ==> exists z . (serves(z, y) /\ inCountry(z, x))))""")

    q.scope match
      case Formula.Forall(_, Formula.Imp(_, Formula.Exists(_, _))) => // Success
      case _ => fail("Expected nested quantifiers")
  }

  // ==================== Whitespace Handling ====================

  test("parse handles extra whitespace") {
    val q = parseOk("""Q[~]^{1/2}  x  ( country(x) ,  large(x) )""")
    assertQueryStructure(q, "x", "country", Nil)
  }

  test("parse handles no whitespace around operators") {
    val q = parseOk("Q[~]^{1/2} x(country(x),large(x))")
    assertQueryStructure(q, "x", "country", Nil)
  }

  test("parse handles multiline input") {
    val q = parseOk("""Q[>=]^{3/4} x 
                    (country(x), 
                     exists y . (hasGDP_agr(x, y) /\ y <= 20))""")
    assertQueryStructure(q, "x", "country", Nil)
  }

  // ==================== Either API Tests ====================

  test("parse succeeds on valid query") {
    val result = parse("Q[~]^{1/2} x (country(x), large(x))")

    result match
      case Right(query) =>
        assertQueryStructure(query, "x", "country", Nil)
        query.quantifier match
          case Quantifier.About(1, 2, 0.1) => // Success
          case _                           => fail("Expected About(1, 2, 0.1)")
      case Left(error)  =>
        fail(s"Expected success, got error: ${error.formatted}")
  }

  test("parse returns error on invalid quantifier operator") {
    val result = parse("Q[==]^{1/2} x (country(x), large(x))")

    result match
      case Left(error) =>
        assertEquals(
          error,
          QueryError.ParseError(
            ParseRefusal.UnknownQuantifierOperator(TokenKind.UnknownOperator),
            SourcePosition.Unknown,
          ),
        )
        assertEquals(error.message, "Invalid quantifier operator: unknown operator (expected ~, >=, or <=)")
      case Right(_)    =>
        fail("Expected parse error")
  }

  test("parse returns error on unexpected tokens") {
    val result = parse("Q[~]^{1/2} x (country(x), large(x)) extra tokens")

    result match
      case Left(error) =>
        assert(error.message.contains("Unexpected tokens"))
        assert(error.message.contains("extra"))
      case Right(_)    =>
        fail("Expected parse error")
  }

  // -----------------------------------------------------------------------
  // End-to-end tests of quoted node-name literals
  // Quoted node-name literals must round-trip through lex → parse → ParsedQuery
  // as Term.Const carrying the inner content (no surrounding quotes).
  // -----------------------------------------------------------------------

  test("P1: quoted multi-word literal in range arg becomes Term.Const") {
    val q = parseOk("Q[>=]^{2/3} x (leaf_descendant_of(x, \"IT Risk\"), large(x))")
    assertEquals(rangeAtom(q).predicate, "leaf_descendant_of")
    assertEquals(rangeAtom(q).terms.length, 2)
    // First arg is the bound variable x; second arg is the quoted literal.
    assertEquals(rangeAtom(q).terms.head, Term.mkVar("x"))
    assertEquals(rangeAtom(q).terms(1), Term.mkConst("IT Risk"))
  }

  test("P2: single-word quoted literal carries content without surrounding quotes") {
    val q = parseOk(
      "Q[~]^{1/2} x (child_of(x, \"Operations\"), gt_loss(p95(x), 5000000))"
    )
    assertEquals(rangeAtom(q).predicate, "child_of")
    assertEquals(rangeAtom(q).terms.length, 2)
    assertEquals(rangeAtom(q).terms(1), Term.mkConst("Operations"))
  }

  test("P3: mixed bare + quoted args — bare stays Var, quoted becomes Const") {
    val q = parseOk(
      "Q[>=]^{1/2} x (capital(x, \"Vienna\"), large(x))(y)"
    )
    assertEquals(rangeAtom(q).predicate, "capital")
    assertEquals(rangeAtom(q).terms.length, 2)
    assertEquals(rangeAtom(q).terms.head, Term.mkVar("x"))
    assertEquals(rangeAtom(q).terms(1), Term.mkConst("Vienna"))
  }

  // ==================== Typed refusals ====================

  private def refusalOf(input: String): (ParseRefusal, SourcePosition) =
    parse(input) match
      case Left(QueryError.ParseError(refusal, position)) => (refusal, position)
      case other                                          => fail(s"expected a ParseError, got $other")

  private def word(text: String): TokenKind     = TokenKind.Word(WordText(text))
  private def symbol(s: GrammarSymbol): TokenKind = TokenKind.Symbol(s)

  /** Asserts the refusal, that its position is `Unknown`, and its sentence. */
  private def assertRefused(input: String, refusal: ParseRefusal, sentence: String): Unit =
    assertEquals(refusalOf(input), (refusal, SourcePosition.Unknown))
    assertEquals(refusal.message, sentence)

  test("QuantifierExpected carries at most the first seven tokens") {
    assertRefused(
      "x (country(x), large(x))",
      ParseRefusal.QuantifierExpected(
        List(
          word("x"),
          symbol(GrammarSymbol.LParen),
          word("country"),
          symbol(GrammarSymbol.LParen),
          word("x"),
          symbol(GrammarSymbol.RParen),
          symbol(GrammarSymbol.Comma),
        )
      ),
      "Expected quantifier Q[op]^{k/n}, got: x ( country ( x ) ,",
    )
  }

  test("UnknownQuantifierOperator carries a grammar symbol it does not accept") {
    assertRefused(
      "Q[*]^{1/2} x (country(x), large(x))",
      ParseRefusal.UnknownQuantifierOperator(symbol(GrammarSymbol.Times)),
      "Invalid quantifier operator: * (expected ~, >=, or <=)",
    )
  }

  test("IntegerExpected names the part and what was found") {
    assertRefused(
      "Q[~]^{a/2} x (country(x), large(x))",
      ParseRefusal.IntegerExpected(QuantifierPart.Numerator, Found.Token(word("a"))),
      "Expected integer for numerator k, got: a",
    )
    assertRefused(
      "Q[~]^{1/",
      ParseRefusal.IntegerExpected(QuantifierPart.Denominator, Found.EndOfInput),
      "Expected integer for denominator n, got end of input",
    )
  }

  test("IntegerOutOfRange carries the digits") {
    assertRefused(
      "Q[~]^{99999999999/2} x (country(x), large(x))",
      ParseRefusal.IntegerOutOfRange(QuantifierPart.Numerator, WordText("99999999999")),
      "Integer out of range for numerator k: 99999999999",
    )
  }

  test("a fraction or tolerance outside [0, 1] is refused with its typed case") {
    assertRefused(
      "Q[>=]^{5/3} x (country(x), large(x))",
      ParseRefusal.FractionOutOfRange(5, 3),
      "Fraction 5/3 must lie in [0, 1] with a positive denominator",
    )
    assertRefused(
      "Q[>=]^{1/0} x (country(x), large(x))",
      ParseRefusal.FractionOutOfRange(1, 0),
      "Fraction 1/0 must lie in [0, 1] with a positive denominator",
    )
    assertRefused(
      "Q[~]^{1/2}[2.5] x (country(x), large(x))",
      ParseRefusal.ToleranceOutOfRange(2.5),
      "Tolerance 2.5 must lie in [0, 1]",
    )
  }

  test("a fraction and a tolerance on the bounds parse") {
    assertEquals(parseOk("Q[>=]^{0/4} x (country(x), large(x))").quantifier, Quantifier.mkAtLeast(0, 4))
    assertEquals(parseOk("Q[~]^{1/1}[1] x (country(x), large(x))").quantifier, Quantifier.mkAbout(1, 1, 1.0))
  }

  test("a quantifier case constructor does not compile outside its package") {
    assert(compileErrors("vql.quantifier.Quantifier.About(1, 2, 0.1)").nonEmpty)
    assert(compileErrors("vql.quantifier.Quantifier.mkAbout(1, 2, 0.1)").isEmpty)
  }

  test("VariableExpected and AnswerVariableExpected carry what was found") {
    assertRefused(
      "Q[~]^{1/2} (country(x), large(x))",
      ParseRefusal.VariableExpected(Found.Token(symbol(GrammarSymbol.LParen))),
      "Expected variable identifier, got: (",
    )
    assertRefused(
      "Q[~]^{1/2}",
      ParseRefusal.VariableExpected(Found.EndOfInput),
      "Expected variable identifier, got end of input",
    )
    assertRefused(
      "Q[~]^{1/2} x (capital(x, y), large(x))(1)",
      ParseRefusal.AnswerVariableExpected(Found.Token(word("1"))),
      "Expected variable in list, got: 1",
    )
    assertRefused(
      "Q[~]^{1/2} x (capital(x, y), large(x))(y,",
      ParseRefusal.AnswerVariableExpected(Found.EndOfInput),
      "Expected variable in list, got end of input",
    )
  }

  test("SymbolExpected names the symbol, the context and what was found") {
    assertRefused(
      "Q[~]^{1/2} x country(x)",
      ParseRefusal.SymbolExpected(GrammarSymbol.LParen, ParseContext.RangeFormula, Found.Token(word("country"))),
      "Expected '(' before range formula, got 'country'",
    )
    assertRefused(
      "Q[~]^{1/2} x",
      ParseRefusal.SymbolExpected(GrammarSymbol.LParen, ParseContext.RangeFormula, Found.EndOfInput),
      "Expected '(' before range formula, got end of input",
    )
  }

  test("TrailingTokens holds every remaining token") {
    assertRefused(
      "Q[~]^{1/2} x (country(x), large(x)) extra tokens",
      ParseRefusal.TrailingTokens(word("extra"), List(word("tokens"))),
      "Unexpected tokens after query: extra tokens",
    )
  }

  test("a formula failure is Formula(failure), with the foundation's sentence and no prefix") {
    assertRefused("Q[~]^{1/2} x (", ParseRefusal.Formula(SyntaxFailure.FormulaExpected), "formula expected")
  }

  test("a refused literal character is Lexical with the exact offset") {
    val input = "Q[~]^{1/2} x (p(x, \"a<b\"), q(x))"
    assertEquals(
      refusalOf(input),
      (ParseRefusal.Lexical(LexFailure.RefusedLiteralCharacter), SourcePosition.At(input.indexOf('<'))),
    )
    assertEquals(
      ParseRefusal.Lexical(LexFailure.RefusedLiteralCharacter).message,
      "Lexer error: String literal holds a character outside its alphabet",
    )
  }

  test("a word with an apostrophe, a decimal-named variable and each operator parse, and their refusals build") {
    val q = parseOk(
      "Q[~]^{1/2} x (country(x), exists y' . (exists 1.5 . r(x, y', 1.5)) /\\ " +
        "(x + 1) * 2 - 3 / 4 ^ 5 >= 0 /\\ x = x /\\ x < 1 /\\ x <= 1 /\\ x > 0 /\\ x :: x = x)"
    )
    def predicates(f: Formula[FOL]): Set[String] = f match
      case Formula.Atom(a)         => Set(a.predicate)
      case Formula.Not(p)          => predicates(p)
      case Formula.And(p, r)       => predicates(p) ++ predicates(r)
      case Formula.Or(p, r)        => predicates(p) ++ predicates(r)
      case Formula.Imp(p, r)       => predicates(p) ++ predicates(r)
      case Formula.Iff(p, r)       => predicates(p) ++ predicates(r)
      case Formula.Forall(_, p)    => predicates(p)
      case Formula.Exists(_, p)    => predicates(p)
      case Formula.True | Formula.False => Set.empty
    def functions(f: Formula[FOL]): Set[String] =
      def ofTerm(t: Term): Set[String] = t match
        case Term.Fn(name, args) => args.toSet.flatMap(ofTerm) + name
        case _                   => Set.empty
      f match
        case Formula.Atom(a)      => a.terms.toSet.flatMap(ofTerm)
        case Formula.Not(p)       => functions(p)
        case Formula.And(p, r)    => functions(p) ++ functions(r)
        case Formula.Or(p, r)     => functions(p) ++ functions(r)
        case Formula.Imp(p, r)    => functions(p) ++ functions(r)
        case Formula.Iff(p, r)    => functions(p) ++ functions(r)
        case Formula.Forall(_, p) => functions(p)
        case Formula.Exists(_, p) => functions(p)
        case Formula.True | Formula.False => Set.empty
    assertEquals(predicates(q.scope), Set("r", ">=", "=", "<", "<=", ">"))
    assertEquals(functions(q.scope), Set("+", "*", "-", "/", "^", "::"))
    assertEquals(FOLUtil.varFOL(q.scope).toSet, Set("x", "y'", "1.5"))
    (predicates(q.scope) ++ functions(q.scope)).foreach(name =>
      assertEquals(TypeCheckError.UnknownPredicate(SymbolText.of(name)).message, s"unknown predicate: $name")
    )
    Set("y'", "1.5").foreach(name =>
      assertEquals(TypeCheckError.UnboundAnswerVar(VariableText.of(name)).message, s"unbound answer variable: $name")
    )
  }

end VagueQueryParserSpec
