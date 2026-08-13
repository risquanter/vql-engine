package vql.fragment

import logic.{FOL, Formula, Term}
import Fragment.*
import FragmentViolation.*

/** Structural membership test over a parsed `Formula[FOL]` — the inert tree
  * `FOLParser.parse` returns. It forks no parser (ADR-007), needs no
  * `TypeCatalog`, and embeds no sort logic. Callers with query text parse via
  * `FOLParser.parse` and map the foundation `ParseError` at their own boundary,
  * the same input discipline as `VagueSemantics.satisfyingSet` (ADR-017 §6).
  */
object FragmentCheck:

  /** `Right(())` iff `formula` lies in `fragment`; `Left` carries the first
    * violated rule in pre-order, left-to-right traversal (ADR-018).
    */
  def check(formula: Formula[FOL], fragment: Fragment): Either[FragmentViolation, Unit] =
    fragment match
      case Targeting    => checkTargeting(formula)
      case Screening(k) =>
        val found = quantifierDepth(formula)
        if found <= k then Right(()) else Left(QuantifierDepthExceeded(k, found))

  /** No quantifier node anywhere, and no `Term.Fn` in any atom's terms. The
    * quantifier node is reported before its body (pre-order), and within an
    * atom the leftmost offending term wins.
    */
  private def checkTargeting(formula: Formula[FOL]): Either[FragmentViolation, Unit] =
    formula match
      case Formula.False | Formula.True   => Right(())
      case Formula.Atom(FOL(_, terms))    => firstFnViolation(terms)
      case Formula.Not(p)                 => checkTargeting(p)
      case Formula.And(p, q)              => checkTargeting(p).flatMap(_ => checkTargeting(q))
      case Formula.Or(p, q)               => checkTargeting(p).flatMap(_ => checkTargeting(q))
      case Formula.Imp(p, q)              => checkTargeting(p).flatMap(_ => checkTargeting(q))
      case Formula.Iff(p, q)              => checkTargeting(p).flatMap(_ => checkTargeting(q))
      case Formula.Forall(_, _)           => Left(QuantifierNotAllowed)
      case Formula.Exists(_, _)           => Left(QuantifierNotAllowed)

  /** The first `Term.Fn` in `terms`, left-to-right. An outermost `Fn` is a
    * violation on its own, so its arguments are not inspected. `Var` and `Const`
    * are admitted.
    */
  private def firstFnViolation(terms: List[Term]): Either[FragmentViolation, Unit] =
    terms.foldLeft[Either[FragmentViolation, Unit]](Right(())) { (acc, t) =>
      acc.flatMap(_ => firstFn(t))
    }

  private def firstFn(term: Term): Either[FragmentViolation, Unit] =
    term match
      case Term.Var(_)     => Right(())
      case Term.Const(_)   => Right(())
      case Term.Fn(name, _) => Left(FunctionApplicationNotAllowed(name))

  /** Maximum number of `Forall`/`Exists` nodes on any single root-to-leaf path.
    * Nesting adds one level; siblings take the larger. Atom terms carry no
    * quantifiers, so they contribute 0.
    */
  private def quantifierDepth(formula: Formula[FOL]): Int =
    formula match
      case Formula.False | Formula.True | Formula.Atom(_) => 0
      case Formula.Not(p)      => quantifierDepth(p)
      case Formula.And(p, q)   => math.max(quantifierDepth(p), quantifierDepth(q))
      case Formula.Or(p, q)    => math.max(quantifierDepth(p), quantifierDepth(q))
      case Formula.Imp(p, q)   => math.max(quantifierDepth(p), quantifierDepth(q))
      case Formula.Iff(p, q)   => math.max(quantifierDepth(p), quantifierDepth(q))
      case Formula.Forall(_, p) => 1 + quantifierDepth(p)
      case Formula.Exists(_, p) => 1 + quantifierDepth(p)
