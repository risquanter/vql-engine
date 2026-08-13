package vql.fragment

/** A structural sub-language of FOL that a formula's parse tree may or may not
  * lie within. Membership is decided by [[FragmentCheck.check]] and depends only
  * on the tree's shape — no sorts, no model.
  */
enum Fragment:
  /** No quantifier nodes and no function-application terms. Admits variables and
    * inline literals (`Term.Const`); rejects every `Term.Fn`, arithmetic
    * operators included.
    */
  case Targeting

  /** Quantifier nesting depth (quantifier rank) at most `maxQuantifierDepth`,
    * 0-indexed: the largest number of `Forall`/`Exists` nodes on any single
    * root-to-leaf path. Side-by-side quantifiers do not add; only nesting does.
    * `Screening(0)` admits no quantifiers. `k` is expected to be non-negative;
    * a negative `k` rejects every formula.
    */
  case Screening(maxQuantifierDepth: Int)

/** The specific fragment rule a formula broke. Returned as the `Left` of
  * [[FragmentCheck.check]], carrying the first violation in pre-order,
  * left-to-right traversal. Mirrors the typed-error house style of
  * `vql.typed.TypeCheckError`.
  */
enum FragmentViolation:
  /** Targeting: a `Forall`/`Exists` node is present. */
  case QuantifierNotAllowed
  /** Targeting: a `Term.Fn` is present; `function` is that application's name. */
  case FunctionApplicationNotAllowed(function: String)
  /** Screening: the formula's maximum quantifier nesting depth `found` exceeds
    * the fragment's `limit`.
    */
  case QuantifierDepthExceeded(limit: Int, found: Int)
