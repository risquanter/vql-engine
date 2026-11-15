# Implementation Plan: Bind-Error Detail Enrichment

**Status:** DONE (2026-08-31). Suite green both platforms (804/804).
**Date:** 2026-08-31
**Target version:** 0.17.0 (follows 0.16.0, ADR-020). Breaking pre-1.0 minor
bump (early-semver): a `BindErrorDetail` case is removed.
**Origin:** Design-quality follow-up to ADR-019/ADR-020. ADR-019 gave one
structured `BindErrorDetail` case (`UnparseableConstant`) and folded the other
ten `TypeCheckError` variants to `Other(rendered)`. This plan finishes that
co-location for all 11 variants and drops `Other`, making the facade fold
exhaustive.
**New ADR created by this plan:** [ADR-021](ADR-021.md) (structured detail for
every `TypeCheckError` variant; exhaustive fold).
**Parent ADRs:** [ADR-004](ADR-004.md) (layering — unchanged),
[ADR-006](ADR-006.md) (encoding — unchanged), [ADR-012](ADR-012.md) (error
channel — unchanged), [ADR-015](ADR-015.md) (sort crosses as `TypeId.value` —
unchanged), [ADR-019](ADR-019.md) (the one-case origin this generalises),
[ADR-020](ADR-020.md) (the multi-element list this enriches).
**Downstream consumer:** register. Reads `detail.sortName` and `detail.rendered`;
classifies `UnparseableConstant` on the `Node` sort as UNKNOWN_REFERENCE, else
BIND_FAILED. The datum register needs (`sortName` on `UnparseableConstant`) is
unchanged. The break is the removal of the `BindErrorDetail.Other` case — see
§6 and the handoff note.

---

## 0. Executive Summary

**What changes and why.** `BindErrorDetail` (in `vql.error`) had two cases:
`UnparseableConstant`, carrying the sort name a consumer needs, and a catch-all
`Other(rendered)` that kept only the rendered string for the other ten
`TypeCheckError` variants. That is the exact "discard a field the consumer would
re-derive" smell ADR-019 introduced the structured detail to remove — applied to
ten of the eleven variants. It also made the facade fold non-exhaustive: a
wildcard sent everything except `UnparseableConstant` to `Other`, so a new
`TypeCheckError` variant would compile silently and lose its fields.

**Scope of the change.** Give `BindErrorDetail` one case per `TypeCheckError`
variant (11 cases), each holding that variant's fields as primitives plus its
`rendered` string, and remove `Other`. Rewrite the facade fold
(`VagueSemantics.toBindErrorDetail`) as an exhaustive 11-arm match with no
wildcard. `renderTypeError` — the single source of rendered strings (ADR-019 §3)
— is untouched, so every `rendered`, and therefore `messages`/`message`, is
byte-identical.

**Payoff.** Every bind error crosses the boundary with its fields intact (the
mismatched sorts of a `TypeMismatch`, the arity pair of an `ArityMismatch`, the
variable name of an `UnconstrainedVar`), so no consumer parses them back out of
the rendered string. The fold becomes a forcing function: a 12th `TypeCheckError`
variant is a compile error in the facade until it is projected, instead of
silently flattening to `Other`.

**Not in scope.** No change to `renderTypeError`, to the rendered strings, to
`BindError`'s shape (`details` + derived `messages`/`message`/`context`), to the
binder's accumulation (ADR-020), or to the layering. E3 (relocate
`TypeCheckError`/`TypeId` into `vql.error`) stays rejected — the boundary is
still crossed with primitives.

---

## 1. ADR Compliance Review (Planning Phase)

Per-ADR verdict over the whole active corpus (WORKING-INSTRUCTIONS requires the
full table for a cross-cutting error-layer change).

| ADR | Verdict | Basis |
|---|---|---|
| ADR-001 (many-sorted binding) | Relevant, compliant | Same typed IL; only the shape of the error detail on the left changes, never the success path. |
| ADR-002 (parser-combinator style) | Not relevant | No parser change. |
| ADR-003 (HDR sampling) | Not relevant | Bind phase only; sampling untouched. |
| ADR-004 (layering) | Relevant, compliant | Every new `BindErrorDetail` field is a primitive; the fold runs in the facade, which already imports both sides. No import direction changes. |
| ADR-006 (enum vs sealed trait) | Relevant, compliant | `BindErrorDetail` stays a pure-data `enum` sharing the abstract `rendered` accessor; the case parameters implement it (§3 clash rule respected). |
| ADR-007 (OCaml-ported core) | Not relevant | `QueryError`/`VagueSemantics` are vague-layer, not in the ported scope tables. |
| ADR-012 (error channel: require vs Either) | Relevant, compliant | `BindError` stays on the `Either` left channel; only the detail cases change. |
| ADR-014 (domain-type quantifiability) | Relevant, compliant | `TypeNotQuantifiable` now has its own structured case instead of folding to `Other`; its rendered string is unchanged. |
| ADR-015 (symmetric value boundaries) | Relevant, compliant | Sorts still cross as `TypeId.value`; the enrichment adds more primitive fields, no new carrier and no `asInstanceOf`. |
| ADR-016 (carrier witness) | Not relevant, Proposed | Value-typeclass concern; untouched. |
| ADR-017 (formula ranges / satisfying set) | Relevant, compliant | `satisfyingSet`'s `BindError` construction reuses the same `toBindErrorDetail`; the ordered-failure chain (UnconstrainedVar → UnexpectedFreeVar → TypeNotQuantifiable) is unaffected, only each error's detail is now structured. |
| ADR-018 (fragment membership) | Not relevant | Structural check over the parse tree; no binder involvement. |
| ADR-019 (structured bind-error detail) | Relevant, extended | This plan generalises ADR-019's one structured case to all 11 and drops `Other`. ADR-019 §1 gets a pointer to ADR-021; the layering decision is unchanged. |
| ADR-020 (binder error accumulation) | Relevant, compliant | The multi-element `details` list ADR-020 fills now carries a per-variant structured detail for each accumulated error. |

**Deviations detected:** None. **New ADR:** ADR-021 records the per-variant
structure and the exhaustive fold.

---

## 2. Current Behavior (grounding)

`BindErrorDetail` (before):

```scala
enum BindErrorDetail:
  case UnparseableConstant(name: String, sortName: String, sourceText: String, rendered: String)
  case Other(rendered: String)
  def rendered: String
```

`toBindErrorDetail` (before) — one structured arm, one wildcard:

```scala
private def toBindErrorDetail(error: TypeCheckError): BindErrorDetail =
  error match
    case TypeCheckError.UnparseableConstant(name, sort, sourceText) =>
      BindErrorDetail.UnparseableConstant(name, sort.value, sourceText, renderTypeError(error))
    case _ =>
      BindErrorDetail.Other(renderTypeError(error))
```

The 11 `TypeCheckError` variants (`vql/typed/TypeCheckError.scala`):
`UnknownPredicate`, `UnknownFunction`, `ArityMismatch(symbol, expected, actual)`,
`UnknownConstantOrLiteral`, `TypeMismatch(expected, actual, context)`,
`UnboundAnswerVar`, `UnconstrainedVar`, `ConflictingTypes(name, left, right)`,
`UnparseableConstant(name, sort, sourceText)`, `TypeNotQuantifiable`,
`UnexpectedFreeVar`.

---

## 3. Target Behavior

`BindErrorDetail` (after) — 11 cases, no `Other`; see ADR-021 §1 for the full
enum. Field mapping, each carrying `rendered`:

| `TypeCheckError` | `BindErrorDetail` case | Non-`rendered` fields |
|---|---|---|
| `UnparseableConstant(name, sort, sourceText)` | `UnparseableConstant` | `name`, `sortName = sort.value`, `sourceText` |
| `TypeMismatch(expected, actual, context)` | `TypeMismatch` | `expectedSort = expected.value`, `actualSort = actual.value`, `context` |
| `ConflictingTypes(name, left, right)` | `ConflictingTypes` | `name`, `leftSort = left.value`, `rightSort = right.value` |
| `ArityMismatch(symbol, expected, actual)` | `ArityMismatch` | `symbol`, `expected`, `actual` |
| `UnknownPredicate(name)` | `UnknownPredicate` | `name` |
| `UnknownFunction(name)` | `UnknownFunction` | `name` |
| `UnknownConstantOrLiteral(name)` | `UnknownConstantOrLiteral` | `name` |
| `UnboundAnswerVar(name)` | `UnboundAnswerVar` | `name` |
| `UnconstrainedVar(name)` | `UnconstrainedVar` | `name` |
| `TypeNotQuantifiable(name)` | `TypeNotQuantifiable` | `name` |
| `UnexpectedFreeVar(name)` | `UnexpectedFreeVar` | `name` |

`toBindErrorDetail` (after): exhaustive 11-arm match, no wildcard (ADR-021 §2).

---

## 4. Implementation Steps

1. `vql/error/QueryError.scala` — replace the 2-case `BindErrorDetail` with the
   11-case enum; keep the abstract `def rendered`. Update the enum scaladoc to
   state the per-variant, exhaustive-projection shape.
2. `vql/semantics/VagueSemantics.scala` — replace the 2-arm fold with the
   exhaustive 11-arm fold, no `case _`. `renderTypeError` unchanged.
3. Tests — update the AC-A conjunction test to match `ArityMismatch` (was
   `Other`); strengthen AC-E to assert the `ConflictingTypes` structured
   projection; leave all `rendered`/`messages`/`details.length` assertions
   (byte-identical strings, ADR-020 accumulation).
4. ADR-021 (new); ADR-019 §1 pointer; CHANGELOG 0.17.0; version bump; this plan.
5. Register handoff note (`docs/scratch/register-*.md`).

---

## 5. Verification

- Full suite, both platforms (JVM + Scala.js): 804/804 green.
- `rendered`/`messages` byte-identical (AC-3, AC-5, AC-C assertions unchanged).
- Exhaustive fold: no `case _`; adding a `TypeCheckError` variant fails to
  compile in `toBindErrorDetail` until projected.

---

## 6. Downstream (register) Impact

Breaking, because a public `BindErrorDetail` case is removed.

- **`sortName` on `UnparseableConstant`** — unchanged; register's
  UNKNOWN_REFERENCE-vs-BIND_FAILED classification keeps reading it.
- **`BindErrorDetail.Other` removed** — any register match on `Other` no longer
  compiles. register replaces an `Other` arm with either a wildcard (`case _`)
  or the specific new cases it wants to classify.
- **New cases available** — register may now read structured fields
  (`ArityMismatch.expected/actual`, `TypeMismatch.expectedSort/actualSort`,
  `ConflictingTypes.leftSort/rightSort`, the `name`-bearing cases) instead of
  parsing `rendered`. Optional; not forced.
- register re-pins to 0.17.0 and adapts its `BindErrorDetail` match. See the
  handoff note in `docs/scratch/`.
