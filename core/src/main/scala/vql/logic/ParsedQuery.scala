package vql.logic

import logic.{FOL, FOLUtil, Formula, Term}
import vql.error.QueryError
import vql.quantifier.Quantifier

/**
 * Vague quantifier query from paper Definition 1 (Section 5.2)
 *
 * Syntax: Q x (R(x,y'), φ(x,y))
 *
 * OCaml-style: case class for product type (like FOL) OCaml reference: fol.ml has "type fol = R of
 * string * term list"
 *
 * Paper reference: Definition 1 (Section 5.2) "A vague query is of the form Q x (R(x,y'), φ(x,y))
 * where:
 *   - Q is a vague quantifier Q[op]^{k/n}
 *   - x is the quantified variable
 *   - R(x,y') is the range formula
 *   - φ(x,y) is the scope predicate (FOL formula)
 *   - y are answer variables (y' ⊆ y)"
 *
 * The range is a full FOL formula (ADR-017): its satisfying set over the quantified variable's sort
 * is the population the quantifier ranges over. A single-atom range is `Formula.Atom(R(x, y'))`.
 *
 * @param quantifier
 *   Type of vague quantifier (Q[op]^{k/n})
 * @param variable
 *   Quantified variable (x)
 * @param range
 *   Range formula R(x,y') as a FOL formula
 * @param scope
 *   Scope predicate φ(x,y) as FOL formula
 * @param answerVars
 *   Free variables y (answer variables)
 */
case class ParsedQuery(
  quantifier: Quantifier,
  variable: String,
  range: Formula[FOL],
  scope: Formula[FOL],
  answerVars: List[String] = Nil):

  /**
   * Free variables of the range formula R(x,y').
   *
   * Inner range quantifiers bind their own variables, so those are excluded (ADR-017 §4):
   * validation is stated over the free variables via `FOLUtil.fvFOL`.
   */
  def rangeVars: Set[String] =
    FOLUtil.fvFOL(range).toSet

  /**
   * All variables occurring in the scope formula φ(x,y), free and bound.
   *
   * Uses `FOLUtil.varFOL`, which includes variables bound by inner `forall`/`exists` — this reports
   * every variable appearing in the scope regardless of binding.
   */
  def scopeVars: Set[String] =
    FOLUtil.varFOL(scope).toSet

  /**
   * Check if query is Boolean (no answer variables)
   *
   * Paper reference: Example 3 distinguishes Boolean vs unary queries
   */
  def isBoolean: Boolean = answerVars.isEmpty

  /** Check if query is unary (single answer variable) */
  def isUnary: Boolean = answerVars.length == 1

end ParsedQuery

object ParsedQuery:

  /**
   * Smart constructor with validation (ADR-017 §4).
   *
   * OCaml reference: formulas.ml has mk_* constructors OCaml pattern: let mk_and p q = And(p,q)
   *
   * Validation, stated over the range formula's free variables:
   *   1. the quantified variable `x` must occur free in `r`;
   *   2. every other free range variable must be an answer variable (`y' ⊆ y`).
   *
   * @param q
   *   Quantifier Q[op]^{k/n}
   * @param x
   *   Quantified variable
   * @param r
   *   Range formula R(x,y')
   * @param phi
   *   Scope predicate φ(x,y)
   * @param y
   *   Answer variables
   * @return
   *   Validated ParsedQuery
   * @throws QueryException
   *   if validation fails
   */
  def mk(
    q: Quantifier,
    x: String,
    r: Formula[FOL],
    phi: Formula[FOL],
    y: List[String],
  ): ParsedQuery =
    val query = ParsedQuery(q, x, r, phi, y)

    // Validation: x must occur free in the range formula
    if !query.rangeVars.contains(x) then
      throw vql.error.QueryException(
        QueryError.ValidationError(
          s"Quantified variable '$x' must occur free in the range formula",
          "quantified_variable",
          Map(
            "variable"   -> x,
            "range_vars" -> query.rangeVars.mkString(", "),
          ),
        )
      )

    // Paper constraint: y' ⊆ y (free range vars minus x ⊆ answer vars)
    val rangeVarsMinusX = query.rangeVars - x
    if !rangeVarsMinusX.subsetOf(y.toSet) then
      throw vql.error.QueryException(
        QueryError.ValidationError(
          s"Range variables ${rangeVarsMinusX.mkString(",")} must be subset of answer variables ${y.mkString(",")}",
          "answer_variables",
          Map(
            "range_vars"   -> rangeVarsMinusX.mkString(", "),
            "answer_vars"  -> y.mkString(", "),
            "missing_vars" -> (rangeVarsMinusX -- y.toSet).mkString(", "),
          ),
        )
      )

    query

  end mk

  /**
   * Single-atom source-compatibility overload: wraps a bare range atom in `Formula.Atom` before
   * validation (ADR-017 §5).
   */
  def mk(
    q: Quantifier,
    x: String,
    r: FOL,
    phi: Formula[FOL],
    y: List[String] = Nil,
  ): ParsedQuery =
    mk(q, x, Formula.Atom(r), phi, y)

  /**
   * Example: q₁ from paper (Boolean query)
   *
   * Q[≥]^{3/4} x (country(x), ∃y (hasGDP_agr(x,y) ∧ y≤20))
   *
   * Paper reference: Example 3, query q₁ "Do at least about three quarters of countries have
   * agricultural GDP ≤ 20%?"
   */
  def example1: ParsedQuery =
    import Formula.*, Term.*
    ParsedQuery(
      quantifier = Quantifier.mkAtLeast(3, 4),
      variable = "x",
      range = Atom(FOL("country", List(Var("x")))),
      scope = Exists(
        "y",
        And(
          Atom(FOL("hasGDP_agr", List(Var("x"), Var("y")))),
          Atom(FOL("<=", List(Var("y"), Const("20")))),
        ),
      ),
      answerVars = Nil, // Boolean query
    )

  end example1

  /**
   * Example: q₃ skeleton from paper (Unary query with answer variable)
   *
   * Q[~#]^{1/2} x (capital(x), ...)(y)
   *
   * Paper reference: Example 3, query q₃ "About half of capitals satisfy some property, grouped by
   * country y"
   */
  def example3Skeleton: ParsedQuery =
    import Formula.*, Term.*
    ParsedQuery(
      quantifier = Quantifier.mkAbout(1, 2),
      variable = "x",
      range = Atom(FOL("capital", List(Var("x")))),
      scope = True, // Placeholder - would be complex formula
      answerVars = List("y"),
    )

  /** Simple example: about half of countries are large */
  def simpleExample: ParsedQuery =
    import Formula.*, Term.*
    ParsedQuery(
      quantifier = Quantifier.aboutHalf,
      variable = "x",
      range = Atom(FOL("country", List(Var("x")))),
      scope = Atom(FOL("large", List(Var("x")))),
      answerVars = Nil,
    )

end ParsedQuery
