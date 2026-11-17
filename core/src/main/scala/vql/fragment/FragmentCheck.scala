package vql.fragment

import Fragment.*
import FragmentViolation.*
import lexer.{LiteralText, SymbolText}
import logic.{FOL, Formula, Term}
import vql.id.SymbolName

/**
 * Structural membership test over a parsed `Formula[FOL]` — the inert tree `FOLParser.parse`
 * returns — and the listing of a function's literal arguments in it. It forks no parser (ADR-007),
 * needs no `TypeCatalog`, and embeds no sort logic. A consumer that admits functions in `Targeting`
 * computes the set with `TypeCatalog.domainReturningFunctions` and passes it in. Callers with query
 * text parse via `FOLParser.parse` and map the foundation `ParseError` at their own boundary, the
 * same input discipline as `VagueSemantics.satisfyingSet` (ADR-017 §6). Its cases `Syntax`,
 * `Trailing` and `Lex` each carry a typed value and a `SourcePosition` (ADR-022).
 */
object FragmentCheck:

  /** `Right(())` iff `formula` lies in `fragment`; `Left` carries the first element of `violations`. */
  def check(formula: Formula[FOL], fragment: Fragment): Either[FragmentViolation, Unit] =
    violations(formula, fragment) match
      case Nil        => Right(())
      case first :: _ => Left(first)

  /**
   * Every rule `formula` breaks in `fragment`, in pre-order, left-to-right traversal, one element per
   * occurrence. Empty iff `formula` lies in `fragment`. The targeting walk continues below a
   * quantifier and into a refused application's arguments.
   * {{{
   * // Targeting(Set(SymbolName("node_id")), Set(SymbolName("mitigated")))
   * forall y. Mitigated(y) /\ p95(node_named(y))
   * // List(QuantifierNotAllowed, PredicateNotAllowed(Mitigated),
   * //      FunctionApplicationNotAllowed(p95), FunctionApplicationNotAllowed(node_named))
   * }}}
   */
  def violations(formula: Formula[FOL], fragment: Fragment): List[FragmentViolation] =
    fragment match
      case Targeting(admitted, forbidden) => targetingViolations(formula, admitted, forbidden)
      case Screening(k)                   =>
        val found = quantifierDepth(formula)
        if found <= k then Nil else List(QuantifierDepthExceeded(k, found))

  /**
   * The literal argument of every application of `function` whose only argument is a literal, left
   * to right, repeats kept, below quantifiers and inside other applications.
   * {{{
   * eq(x, node_id("B")) \/ child_of(x, node_id("A")) \/ eq(x, node_id("B"))
   * // literalArguments(f, SymbolName("node_id")) == List(B, A, B)
   * }}}
   */
  def literalArguments(formula: Formula[FOL], function: SymbolName): List[LiteralText] =
    atomTerms(formula).flatMap(literalsIn(_, function))

  /**
   * Every quantifier node, forbidden predicate and refused `Term.Fn`, in pre-order. A quantifier is
   * reported before its body, an atom's predicate before its terms, and a refused application before
   * its arguments.
   */
  private def targetingViolations(
    formula: Formula[FOL],
    admitted: Set[SymbolName],
    forbidden: Set[SymbolName],
  ): List[FragmentViolation] =
    formula match
      case Formula.False | Formula.True        => Nil
      case Formula.Atom(FOL(predicate, terms)) =>
        predicateViolation(predicate, forbidden) ++ terms.flatMap(termViolations(_, admitted))
      case Formula.Not(p)                      => targetingViolations(p, admitted, forbidden)
      case Formula.And(p, q)                   =>
        targetingViolations(p, admitted, forbidden) ++ targetingViolations(q, admitted, forbidden)
      case Formula.Or(p, q)                    =>
        targetingViolations(p, admitted, forbidden) ++ targetingViolations(q, admitted, forbidden)
      case Formula.Imp(p, q)                   =>
        targetingViolations(p, admitted, forbidden) ++ targetingViolations(q, admitted, forbidden)
      case Formula.Iff(p, q)                   =>
        targetingViolations(p, admitted, forbidden) ++ targetingViolations(q, admitted, forbidden)
      case Formula.Forall(_, p)                => QuantifierNotAllowed :: targetingViolations(p, admitted, forbidden)
      case Formula.Exists(_, p)                => QuantifierNotAllowed :: targetingViolations(p, admitted, forbidden)

  private def predicateViolation(predicate: String, forbidden: Set[SymbolName]): List[FragmentViolation] =
    Option
      .when(forbidden.exists(name => lowerCase(name.value) == lowerCase(predicate)))(
        PredicateNotAllowed(SymbolText.of(predicate))
      )
      .toList

  /** `s` with each character lower-cased by `Character.toLowerCase`, which ignores the default locale. */
  private def lowerCase(s: String): String = s.map(Character.toLowerCase)

  /**
   * The refused `Term.Fn`s in `term`, outermost first, descending into every application.
   * {{{
   * // admitted = Set(SymbolName("node_id"))
   * node_id(x)            // Nil
   * p95(node_named(x))    // List(FunctionApplicationNotAllowed(p95), FunctionApplicationNotAllowed(node_named))
   * }}}
   */
  private def termViolations(term: Term, admitted: Set[SymbolName]): List[FragmentViolation] =
    term match
      case Term.Var(_) | Term.Const(_)                                => Nil
      case Term.Fn(name, args) if admitted.contains(SymbolName(name)) =>
        args.flatMap(termViolations(_, admitted))
      case Term.Fn(name, args)                                        =>
        FunctionApplicationNotAllowed(SymbolText.of(name)) :: args.flatMap(termViolations(_, admitted))

  /** The terms of every atom in `formula`, left to right, below quantifiers too. */
  private def atomTerms(formula: Formula[FOL]): List[Term] =
    formula match
      case Formula.False | Formula.True => Nil
      case Formula.Atom(FOL(_, terms))  => terms
      case Formula.Not(p)               => atomTerms(p)
      case Formula.And(p, q)            => atomTerms(p) ++ atomTerms(q)
      case Formula.Or(p, q)             => atomTerms(p) ++ atomTerms(q)
      case Formula.Imp(p, q)            => atomTerms(p) ++ atomTerms(q)
      case Formula.Iff(p, q)            => atomTerms(p) ++ atomTerms(q)
      case Formula.Forall(_, p)         => atomTerms(p)
      case Formula.Exists(_, p)         => atomTerms(p)

  private def literalsIn(term: Term, function: SymbolName): List[LiteralText] =
    term match
      case Term.Fn(name, Term.Const(literal) :: Nil) if name == function.value => List(LiteralText(literal))
      case Term.Fn(_, args)                                                   => args.flatMap(literalsIn(_, function))
      case Term.Var(_) | Term.Const(_)                                        => Nil

  /**
   * Maximum number of `Forall`/`Exists` nodes on any single root-to-leaf path. Nesting adds one
   * level; siblings take the larger. Atom terms carry no quantifiers, so they contribute 0.
   */
  private def quantifierDepth(formula: Formula[FOL]): Int =
    formula match
      case Formula.False | Formula.True | Formula.Atom(_) => 0
      case Formula.Not(p)                                 => quantifierDepth(p)
      case Formula.And(p, q)    => math.max(quantifierDepth(p), quantifierDepth(q))
      case Formula.Or(p, q)     => math.max(quantifierDepth(p), quantifierDepth(q))
      case Formula.Imp(p, q)    => math.max(quantifierDepth(p), quantifierDepth(q))
      case Formula.Iff(p, q)    => math.max(quantifierDepth(p), quantifierDepth(q))
      case Formula.Forall(_, p) => 1 + quantifierDepth(p)
      case Formula.Exists(_, p) => 1 + quantifierDepth(p)

end FragmentCheck
