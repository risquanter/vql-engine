# PLAN — Fragment-Membership API (T-011, 0.12.0)

**Status:** In progress.
**Workstream:** register-facing fragment-membership check on the FOL parse tree.
**Tracks:** [TODOS.md T-011](TODOS.md). Ships as the additive, non-breaking Central
release **0.12.0**, ahead of the T-000 package rename (0.13.0).
**ADR:** [ADR-018](ADR-018.md) (structural fragment membership over the parse tree).

---

## 1. Objective

Add a public, structural membership test over a parsed `Formula[FOL]` — the inert
tree `FOLParser.parse` returns — that decides whether the formula lies inside a
declared fragment, returning the first violated rule when it does not. register
calls it at its HTTP write-path boundary to reject out-of-fragment formulas with
a specific 400 before the formula reaches typed bind.

Two fragments ship in 0.12.0 (both, per the 2026-08-12 ruling):

- **`Targeting`** — no quantifier nodes, no function-application terms.
- **`Screening(k)`** — maximum quantifier nesting depth ≤ `k` (0-indexed).

The check is a parse-tree walk. It forks no parser (ADR-007), needs no
`TypeCatalog`, and embeds no sort logic (sort enforcement is register's separate
bind-time rule).

## 2. Public API (ruled contract, T-011)

```scala
package fol.fragment

enum Fragment:
  case Targeting                          // no quantifiers, no function-application terms
  case Screening(maxQuantifierDepth: Int) // max quantifier nesting depth ≤ k (0-indexed)

enum FragmentViolation:
  case QuantifierNotAllowed                            // targeting: a Forall/Exists node is present
  case FunctionApplicationNotAllowed(function: String) // targeting: a Term.Fn is present
  case QuantifierDepthExceeded(limit: Int, found: Int) // screening: nesting depth > k

object FragmentCheck:
  def check(formula: Formula[FOL], fragment: Fragment): Either[FragmentViolation, Unit]
```

`check` returns `Right(())` iff `formula` lies in `fragment`; `Left` carries the
first violated rule (return-granularity ruling, 2026-08-11).

### Fixed conventions (single-answer details, documented, not open decisions)

1. **"First" violation = pre-order, left-to-right.** The walk descends the tree
   top-down, left operand before right; the first rule violation encountered is
   returned. Any formula that violates several rules still returns a true
   violation; register maps any specific `FragmentViolation` to a specific 400,
   so the exact one is not correctness-load-bearing — the order is fixed only so
   the result is deterministic.
2. **`FunctionApplicationNotAllowed.function`** carries the name of that first
   (outermost, leftmost) `Term.Fn`. Inline literals are `Term.Const` and are
   admitted; only `Term.Fn` (including arithmetic operators `+ - * / ^ ::`,
   unary minus, and nullary `f()`) is rejected by `Targeting`.
3. **Depth is quantifier rank, 0-indexed** (2026-08-12 ruling): the largest
   number of `Forall`/`Exists` nodes on any single root-to-leaf path.
   Side-by-side quantifiers do not add; only nesting does. `Screening(0)` admits
   no quantifiers — identical to targeting's quantifier rule; `∃` inside `∃`
   needs `Screening(2)`. `QuantifierDepthExceeded.found` is the formula's actual
   maximum nesting depth.
4. **`Screening(k)` with `k < 0`** is total and degenerate: every formula has
   depth ≥ 0 > k, so all are rejected with `QuantifierDepthExceeded`. `k ≥ 0` is
   the documented expectation; no `require` guards the `enum` case (ADR-006 keeps
   `Fragment` a pure-data sum; see ADR-018 and the ADR-012 note in §6).

## 3. Placement and dependencies

- New package `fol.fragment`, a sibling of `fol.typed`. It imports only the
  foundation (`logic.Formula`, `logic.FOL`, `logic.Term`); it does **not** import
  `fol.typed` or any other vague-layer package. Direction respects ADR-004
  (vague imports foundation, never the reverse), and independence from the typed
  machinery matches the T-011 contract.
- No facade wiring. Unlike `satisfyingSet` (ADR-017 §6), which is a
  `VagueSemantics` method because it needs a model, `FragmentCheck` needs no
  model and is a standalone object. Its input boundary is the same as
  `satisfyingSet`'s: a pre-parsed `Formula[FOL]`; callers with query text parse
  via `FOLParser.parse` and map the foundation `ParseError` at their own
  boundary (ADR-017 §6, ADR-007 C2). No text parsing happens inside `fol.fragment`.

## 4. Phases

### Phase 0 — Plan + ADR (documents only)
This document and [ADR-018](ADR-018.md) (Proposed). No code.

### Phase 1 — Core implementation + unit tests
- `core/src/main/scala/fol/fragment/Fragment.scala` — `Fragment`, `FragmentViolation` enums.
- `core/src/main/scala/fol/fragment/FragmentCheck.scala` — `check` and its private tree walks.
- `core/src/test/scala/fol/fragment/FragmentCheckSpec.scala` — unit tests over
  hand-built `Formula[FOL]` trees: targeting admit/reject (quantifier, `Fn`,
  nested `Fn`, arithmetic, `Const`/`Var` admitted); screening depth boundaries
  (0/1/2, siblings-do-not-add, `Screening(0) ≡ no quantifiers`); first-violation
  order; `found` reporting.

### Phase 2 — End-to-end (parse → check) tests
- Parse register-style strings through `FOLParser.parse`, then `FragmentCheck.check`,
  asserting membership and the specific violation. Covers the public entry path
  a consumer uses and confirms the `Term.Const` vs `Term.Fn` parse facts the
  contract relies on (literals → `Const`, applicative/arithmetic → `Fn`).

### Phase 3 — Docs sweep, version, ADR acceptance
- `docs/Architecture.md` — add `fragment/` to the package map and an ADR-018 row.
- `docs/ADR-004.md` — add `fragment/` to the two-layer diagram.
- `README.md` — one line noting the fragment-membership entry point.
- `docs/TODOS.md` — T-011 status → implemented; keep the contract; note ADR-018.
- `docs/ADR-018.md` — Accepted (2026-08-12).
- `build.sbt` — version `0.11.0` → `0.12.0`; README and Architecture install/publish
  snippets bumped to match. HEAD tracks the version being shipped; the user runs
  the publish.

## 5. Validation checklist (per WORKING-INSTRUCTIONS)

- ADR-004: `fol.fragment` imports foundation only; foundation unchanged. ✓ target
- ADR-006: `Fragment`/`FragmentViolation` are pure-data `enum`s; `FragmentCheck`
  is an operations object. ✓ target
- ADR-007: no parser file touched; no characteristic C1–C13 affected. ✓ target
- ADR-012: `check` returns `Either` as its result (membership-with-reason), no
  `require`; see §6 note on the `Screening` `enum` case. ✓ target
- ADR-017: same pre-parsed-`Formula[FOL]` input boundary; caller maps `ParseError`. ✓ target
- Integration Verification: reachable and exercised through `FOLParser.parse →
  FragmentCheck.check` end-to-end (Phase 2). ✓ target

## 6. ADR compliance review (planning phase)

**Reviewed:** all accepted ADRs (001, 002, 003, 004, 006, 007, 012, 014, 015,
017) and Proposed ADR-016; Deprecated ADRs 005/008/009/010 (untyped backend) do
not apply — this feature touches no evaluation, KB, relation, or sampling code.

**Deviations detected:** none that block. One point recorded for visibility:

- **ADR-012 §1 (construction invariants via `require`) vs ADR-006 (`enum` for
  pure-data sums).** `Screening(maxQuantifierDepth: Int)` could carry a `k ≥ 0`
  construction invariant. It is left un-`require`d because `Fragment` is a
  pure-data `enum` (ADR-006) whose cases cannot host an init body, and the T-011
  contract fixes the bare case shape. This is not an unsafe gap: `check` is total
  for negative `k` (rejects everything via `QuantifierDepthExceeded`), so no
  `IllegalArgumentException` path is being replaced by silent corruption. `k ≥ 0`
  is documented (§2.4, ADR-018). Adding a `Fragment.screening(k)` smart
  constructor with `require` later is additive if register ever wants it.

**Alignment:** the parse-tree-walk placement is the direct consequence of ADR-007
(the Harrison-ported parser core is frozen; a restricted parser would fork it) —
the check accepts exactly the same string set and rejects before typed bind, so
"reject at the language boundary" holds without a parser change. ADR-017 is the
boundary precedent: a public entry over a pre-parsed `Formula[FOL]` with parse
error mapped at the caller.
