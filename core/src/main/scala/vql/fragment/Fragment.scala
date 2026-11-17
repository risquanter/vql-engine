package vql.fragment

import lexer.SymbolText
import vql.id.SymbolName

/**
 * A structural sub-language of FOL that a formula's parse tree may or may not lie within.
 * Membership is decided by [[FragmentCheck.check]] and depends on the tree's shape and on the
 * fragment's own parameters. No sort and no model takes part.
 */
enum Fragment:
  /**
   * No quantifier nodes, no function application outside `admittedFunctions`, and no atom whose
   * predicate is in `forbiddenPredicates`, compared after lower-casing both names. Admits variables
   * and inline literals (`Term.Const`). An admitted application's arguments are walked by the same
   * rule, so a refused function inside an admitted one is still refused. `Targeting(Set.empty,
   * Set.empty)` admits no function at all, arithmetic operators included, and forbids no predicate.
   */
  case Targeting(admittedFunctions: Set[SymbolName], forbiddenPredicates: Set[SymbolName])

  /**
   * Quantifier nesting depth (quantifier rank) at most `maxQuantifierDepth`, 0-indexed: the largest
   * number of `Forall`/`Exists` nodes on any single root-to-leaf path. Side-by-side quantifiers do
   * not add; only nesting does. `Screening(0)` admits no quantifiers. `k` is expected to be
   * non-negative; a negative `k` rejects every formula.
   */
  case Screening(maxQuantifierDepth: Int)

/**
 * The specific fragment rule a formula broke. [[FragmentCheck.check]] carries the first violation
 * as its `Left`, and [[FragmentCheck.violations]] carries all of them, both in pre-order,
 * left-to-right traversal. Mirrors the typed-error house style of `vql.error.TypeCheckError`.
 */
enum FragmentViolation:
  /** Targeting: a `Forall`/`Exists` node is present. */
  case QuantifierNotAllowed

  /** Targeting: a `Term.Fn` outside the admitted set is present; `function` is its name. */
  case FunctionApplicationNotAllowed(function: SymbolText)

  /** Targeting: an atom's predicate is forbidden; `predicate` is its name as the formula wrote it. */
  case PredicateNotAllowed(predicate: SymbolText)

  /**
   * Screening: the formula's maximum quantifier nesting depth `found` exceeds the fragment's
   * `limit`.
   */
  case QuantifierDepthExceeded(limit: Int, found: Int)
