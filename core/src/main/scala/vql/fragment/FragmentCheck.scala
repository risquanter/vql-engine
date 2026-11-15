package vql.fragment

import Fragment.*
import FragmentViolation.*
import lexer.SymbolText
import logic.{FOL, Formula, Term}
import vql.id.SymbolName

/**
 * Structural membership test over a parsed `Formula[FOL]` — the inert tree `FOLParser.parse`
 * returns. It forks no parser (ADR-007), needs no `TypeCatalog`, and embeds no sort logic. A
 * consumer that admits functions in `Targeting` computes the set with
 * `TypeCatalog.domainReturningFunctions` and passes it in. Callers
 * with query text parse via `FOLParser.parse` and map the foundation `ParseError` at their own
 * boundary, the same input discipline as `VagueSemantics.satisfyingSet` (ADR-017 §6). Its cases
 * `Syntax`, `Trailing` and `Lex` each carry a typed value and a `SourcePosition` (ADR-022).
 */
object FragmentCheck:

  /**
   * `Right(())` iff `formula` lies in `fragment`; `Left` carries the first violated rule in
   * pre-order, left-to-right traversal (ADR-018).
   */
  def check(formula: Formula[FOL], fragment: Fragment): Either[FragmentViolation, Unit] =
    fragment match
      case Targeting(admitted) => checkTargeting(formula, admitted)
      case Screening(k)        =>
        val found = quantifierDepth(formula)
        if found <= k then Right(()) else Left(QuantifierDepthExceeded(k, found))

  /**
   * No quantifier node anywhere, and no `Term.Fn` outside `admitted` in any atom's terms. The
   * quantifier node is reported before its body (pre-order), and within an atom the leftmost
   * offending term wins.
   */
  private def checkTargeting(
    formula: Formula[FOL],
    admitted: Set[SymbolName],
  ): Either[FragmentViolation, Unit] =
    formula match
      case Formula.False | Formula.True => Right(())
      case Formula.Atom(FOL(_, terms))  => firstFnViolation(terms, admitted)
      case Formula.Not(p)               => checkTargeting(p, admitted)
      case Formula.And(p, q)            =>
        checkTargeting(p, admitted).flatMap(_ => checkTargeting(q, admitted))
      case Formula.Or(p, q)             =>
        checkTargeting(p, admitted).flatMap(_ => checkTargeting(q, admitted))
      case Formula.Imp(p, q)            =>
        checkTargeting(p, admitted).flatMap(_ => checkTargeting(q, admitted))
      case Formula.Iff(p, q)            =>
        checkTargeting(p, admitted).flatMap(_ => checkTargeting(q, admitted))
      case Formula.Forall(_, _)         => Left(QuantifierNotAllowed)
      case Formula.Exists(_, _)         => Left(QuantifierNotAllowed)

  /**
   * The first refused `Term.Fn` in `terms`, left to right, descending into admitted applications.
   * A refused `Fn` is a violation on its own, so its arguments are not inspected. `Var` and `Const`
   * are admitted.
   * {{{
   * // admitted = Set(SymbolName("node_id"))
   * node_id(x)       // Right(())
   * node_id(p95(x))  // Left(FunctionApplicationNotAllowed(SymbolText.of("p95")))
   * }}}
   */
  private def firstFnViolation(
    terms: List[Term],
    admitted: Set[SymbolName],
  ): Either[FragmentViolation, Unit] =
    terms.foldLeft[Either[FragmentViolation, Unit]](Right(())) { (acc, t) =>
      acc.flatMap(_ => firstFn(t, admitted))
    }

  private def firstFn(term: Term, admitted: Set[SymbolName]): Either[FragmentViolation, Unit] =
    term match
      case Term.Var(_)                                                => Right(())
      case Term.Const(_)                                              => Right(())
      case Term.Fn(name, args) if admitted.contains(SymbolName(name)) =>
        firstFnViolation(args, admitted)
      case Term.Fn(name, _)                                           =>
        Left(FunctionApplicationNotAllowed(SymbolText.of(name)))

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
