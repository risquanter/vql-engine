# Agent prompt — vql-engine: expose bind-error sort so consumers classify with one error mapper

You are making one change in the **vague-quantifier-logic** repository (this repo). Work only in
this repo. Follow its own governance: read `docs/WORKING-INSTRUCTIONS.md`, the ADRs under `docs/`,
and `docs/TODOS.md` before coding. The downstream consumer is the **register** project; the
acceptance criteria below are the interface contract register is designed against — do not deviate
from them without flagging it. **This is a design task with a real layering decision in it: present
a plan (with the options below resolved) and obtain approval before writing code.** Current engine
version: `0.14.0`.

## The consumer problem (why this exists)

A query string flows through three engine phases: parse → bind (type-check) → evaluate. register
maps every failure to an HTTP status. Two bind-phase outcomes must map to **different** results:

- An **unknown quoted node name** — a constant token that is neither a valid node id nor a known
  node name — must become **HTTP 400 `UNKNOWN_REFERENCE`** ("Unknown reference: 'Foo'"). This is a
  normal user mistake (they named a node that does not exist).
- Any **genuine type error** — arity mismatch, non-quantifiable sort, type mismatch, etc. — must
  become **HTTP 400 `BIND_FAILED`** with the rendered type-error text.

register models node names as a **literal validator on a sort it calls `Node`** (a `TypeId` whose
`.value` is `"Node"`). When a quoted token is not a valid node, the binder produces
`TypeCheckError.UnparseableConstant(name, sort = Node, sourceText)`
(`core/src/main/scala/vql/typed/TypeCheckError.scala`). So the distinction register needs is purely:
**"is this bind failure an `UnparseableConstant` on the `Node` sort?"** — the engine does not need
to know anything about register's `Node` sort; it only needs to let the consumer *see the sort*.

## Why the consumer cannot do this today

The high-level entry point `VagueSemantics.evaluateTyped` (the single call register wants to use)
folds the structured bind errors into a `QueryError` before returning
(`core/src/main/scala/vql/semantics/VagueSemantics.scala`, `bindTyped`):

```scala
val unknowns = errors.collect { case TypeCheckError.UnknownConstantOrLiteral(name) => name }
val others   = errors.filterNot { case _: TypeCheckError.UnknownConstantOrLiteral => true; case _ => false }
if others.isEmpty && unknowns.nonEmpty then QueryError.UnknownConstantOrLiteralError(unknowns.head)
else                                        QueryError.BindError(errors = renderTypeErrors(errors))
```

An `UnparseableConstant` falls into `others` and is rendered to a `String` inside
`QueryError.BindError(errors: List[String])`. The **sort is discarded** at that point.
`QueryError.BindError` deliberately carries `List[String]` and not `List[TypeCheckError]` because of
a package-layering rule stated at `core/src/main/scala/vql/error/QueryError.scala:128-129`:
**`vql.error` must not depend on `vql.typed`** (where `TypeCheckError` and `TypeId` live).

Because the sort is gone by the time register holds a `QueryError`, register's only current
workaround is to **bypass `evaluateTyped`** and call the low-level `QueryBinder.bind` itself to read
the raw `List[TypeCheckError]`, then run its own fold *and* duplicate the engine-private
`renderTypeErrors`. That gives register a **two-tier error surface**: a hand-written fold over
`List[TypeCheckError]` for the bind phase, plus its normal `QueryError` mapper for parse/evaluate.
The goal of this task is to let register keep the single `evaluateTyped` call and a **single**
error mapper.

## Goal (interface contract register is built against)

From the `QueryError` returned by `VagueSemantics.evaluateTyped`, a consumer must be able to
determine, without dropping to `QueryBinder.bind`:

1. That a bind failure was caused by one or more `UnparseableConstant` errors, and **for each, the
   sort name** (`TypeId.value`, i.e. the `String` `"Node"`), so the consumer can classify by sort.
2. All existing information still available: the human-readable rendered message per error (so the
   `BIND_FAILED` text is unchanged for consumers that only want the message).
3. `UnknownConstantOrLiteralError` behaviour unchanged (register already maps it to
   `UNKNOWN_REFERENCE`).

The change must **not** introduce a dependency from `vql.error` on `vql.typed`.

## The layering decision — resolve this in your plan

The sort is a `TypeId` in `vql.typed`; `QueryError` is in `vql.error` and may not reach into
`vql.typed`. So the sort has to cross the layer as **primitives** (a `String`), or the layering
itself has to change. Options, with trade-offs:

- **Option E1 — structured detail in the error layer (recommended).** Add a small error-layer type,
  e.g. `enum BindErrorDetail` in `vql.error`, carrying only primitives:
  `UnparseableConstant(name: String, sortName: String, sourceText: String)`,
  `Other(rendered: String)` (and any further cases you want to surface). Change
  `QueryError.BindError` to carry `List[BindErrorDetail]` *and* keep a rendered `List[String]` (or a
  `def messages: List[String]` derived from the details) so the existing message contract holds. The
  fold in `bindTyped`/`satisfyingSet` maps each `TypeCheckError` → `BindErrorDetail`
  (`UnparseableConstant(_, sort, src)` → `BindErrorDetail.UnparseableConstant(name, sort.value, src)`,
  everything else → `Other(rendered)`).
  - Pro: consumer classifies a **mixed** error list by sort exactly as register does today, through
    the single `QueryError`; no layering violation (primitives only); message contract preserved.
  - Con: a second, parallel representation of bind errors lives in the error layer, and the fold
    maps between them.

- **Option E2 — a dedicated homogeneous `QueryError` variant.** Mirror the existing
  `UnknownConstantOrLiteralError` pattern: when **all** bind errors are `UnparseableConstant`, emit a
  new `QueryError.UnparseableConstantError(items: List[(String /*name*/, String /*sortName*/, String /*sourceText*/)])`;
  any mix with other error kinds still folds to `BindError`.
  - Pro: minimal; primitives only; matches the precedent that `UnknownConstantOrLiteralError` is only
    emitted when the whole list is homogeneous.
  - Con: a query that has an unknown node name **and** another type error collapses to `BindError`,
    so the unknown-reference signal is lost in the mixed case. register's current register-side fold
    handles the mixed case; this option is strictly less capable there.

- **Option E3 — relocate `TypeCheckError`/`TypeId` into `vql.error`** so `BindError` can carry
  `List[TypeCheckError]` directly.
  - Pro: consumers get the full structured type; no parallel representation.
  - Con: largest refactor; moves typed-layer concepts down into the error layer and cascades through
    `TypeId`'s usages. Likely disproportionate to the need.

**Recommendation (mine, to be confirmed by the repo owner): Option E1** — it gives register the exact
mixed-list classification power it has now, keeps the rendered-message contract, and stays within the
layering rule using primitives.

## Acceptance criteria

1. A test binds a query whose only bind error is an `UnparseableConstant` on some sort and asserts
   the sort name is recoverable from the `QueryError` returned by `evaluateTyped` (not from
   `QueryBinder.bind`).
2. A test with a **mixed** bind-error list (an `UnparseableConstant` plus, e.g., an `ArityMismatch`)
   asserts both are recoverable / correctly represented per the chosen option.
3. The existing `UnknownConstantOrLiteralError` homogeneous path is unchanged (existing tests green).
4. The rendered human-readable messages for `BindError` are unchanged for a consumer that only reads
   the strings (no regression in `BIND_FAILED` text).
5. No compile dependency from `vql.error` on `vql.typed` is introduced (verify the module/package
   graph still holds).
6. `renderTypeErrors` (or its replacement) remains the single source of the rendered strings — do not
   fork the rendering.

## Versioning & handoff

- This is an external-API change to the public `QueryError` hierarchy. Bump `ThisBuild / version`
  one **MINOR** step under early-semver (`0.14.0` → `0.15.0`) and add a `CHANGELOG.md` entry
  describing the new `QueryError.BindError` shape / new variant and how consumers read the sort.
- register will re-pin to the new version and collapse its two-tier bind-error handling back to a
  single `fromQueryError` mapper (the node-sort classification moving inside that mapper's
  `BindError` case). Keep the contract in "Goal" stable so that collapse is a small register diff.
- Do not weaken or delete any existing engine test to make room for the change; add new tests
  alongside. If the chosen option forces an existing assertion to change, flag it as a decision
  before doing so.
