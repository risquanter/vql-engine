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

register's decision rule over the bind errors is: map to **`UNKNOWN_REFERENCE`** iff *every* bind
error is a node name that does not resolve (an `UnparseableConstant` on the `Node` sort, or an
`UnknownConstantOrLiteral`); otherwise map to **`BIND_FAILED`** with the rendered type-error text. A
query with an unknown node name **and** a genuine type error therefore falls to the `else` branch —
the type error dominates — so **no mixed-case per-error classification is required**. Note also that
`Loss` and `Probability` sorts have validators too, so a non-node `UnparseableConstant` is reachable
(`gt_loss(p95(x), "abc")` → `UnparseableConstant("abc", sort = Loss, …)`;
`gt_prob(lec(x,100), "2.0")` → `UnparseableConstant("2.0", sort = Probability, …)`); both are
`BIND_FAILED`, and register wants the engine's rendered message for the text rather than re-rendering
it. Finally, `UnknownConstantOrLiteral` is effectively unreachable *for register* today: every
user-writable constant position (`Node`, `Loss`, `Probability`) has a literal validator, so an
unrecognized token becomes `UnparseableConstant`, not `UnknownConstantOrLiteral`. The engine's
homogeneous `UnknownConstantOrLiteralError` path is retained for other consumers, and register maps it
to `UNKNOWN_REFERENCE` harmlessly.

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
  e.g. `enum BindErrorDetail` in `vql.error`, carrying only primitives and each case's own **rendered
  string**:
  `UnparseableConstant(name: String, sortName: String, sourceText: String, rendered: String)`,
  `Other(rendered: String)` (and any further cases you want to surface), with a `def rendered: String`
  across the enum. Change `QueryError.BindError` to a **single** field
  `details: List[BindErrorDetail]` and derive the message contract from it — `def messages:
  List[String] = details.map(_.rendered)` — rather than storing a parallel `List[String]` (one
  representation, no drift). The fold in `bindTyped`/`satisfyingSet` maps each `TypeCheckError` →
  `BindErrorDetail` (`UnparseableConstant(_, sort, src)` →
  `BindErrorDetail.UnparseableConstant(name, sort.value, src, renderTypeError(e))`, everything else →
  `Other(renderTypeError(e))`). This requires splitting the current all-at-once
  `renderTypeErrors(List[TypeCheckError]): List[String]` into a per-error
  `renderTypeError(TypeCheckError): String` and mapping over it, so the rendered strings still come
  from one place (see AC #7) — this is a refactor, not a fork.
  - Pro: a single flat bind-error list co-locates each error's sort with its rendered message, so the
    consumer applies its own recoverability predicate over one list without re-rendering; no layering
    violation (primitives only); message contract preserved via a derived `messages`.
  - Con: a second, parallel representation of bind errors lives in the error layer, and the fold
    maps between them.

- **Option E2 — a dedicated homogeneous `QueryError` variant.** Mirror the existing
  `UnknownConstantOrLiteralError` pattern: when **all** bind errors are `UnparseableConstant`, emit a
  new `QueryError.UnparseableConstantError(items: List[(String /*name*/, String /*sortName*/, String /*sourceText*/)])`;
  any mix with other error kinds still folds to `BindError`.
  - Pro: minimal; primitives only; matches the precedent that `UnknownConstantOrLiteralError` is only
    emitted when the whole list is homogeneous.
  - Con: E2's "**all** `UnparseableConstant`" grouping does not match register's "**all
    node-recoverable**" boundary. A homogeneous non-node unparseable — a bad `Loss` or `Probability`
    literal, which is reachable because those sorts have validators — lands in
    `UnparseableConstantError`, which drops the rendered message and forces the consumer to re-render
    the `BIND_FAILED` text (the exact duplication this task removes), unless the variant carries the
    rendered string per item — at which point it is E1 with an extra precondition and no real
    simplification.

- **Option E3 — relocate `TypeCheckError`/`TypeId` into `vql.error`** so `BindError` can carry
  `List[TypeCheckError]` directly.
  - Pro: consumers get the full structured type; no parallel representation.
  - Con: largest refactor; moves typed-layer concepts down into the error layer and cascades through
    `TypeId`'s usages. Likely disproportionate to the need.

**Recommendation (mine, to be confirmed by the repo owner): Option E1** — not because it "classifies
the mixed case," but because its single flat list co-locates each error's sort with its rendered
message, so register applies one recoverability predicate over one list without re-rendering, and
register's recoverability boundary ("all node-recoverable") does not line up with E2's homogeneity
boundary ("all `UnparseableConstant`"). It also keeps the rendered-message contract and stays within
the layering rule using primitives.

## Acceptance criteria

1. A test binds a query whose only bind error is an `UnparseableConstant` on some sort and asserts
   the sort name is recoverable from the `QueryError` returned by `evaluateTyped` (not from
   `QueryBinder.bind`).
2. A test with a **mixed** bind-error list (an `UnparseableConstant` plus, e.g., an `ArityMismatch`)
   asserts the list is represented such that a consumer classifies it `BIND_FAILED` — the genuine
   type error dominates — not that both errors are separately "recoverable."
3. A test with a **homogeneous non-node** `UnparseableConstant` (e.g. a bad `Loss` literal) asserts
   the error is recoverable as a structured detail carrying **both** the sort name and the rendered
   message, so a consumer classifies it `BIND_FAILED` without re-rendering.
4. The existing `UnknownConstantOrLiteralError` homogeneous path is unchanged (existing tests green).
5. The rendered human-readable messages for `BindError` are unchanged for a consumer that only reads
   the strings (no regression in `BIND_FAILED` text).
6. No compile dependency from `vql.error` on `vql.typed` is introduced (verify the module/package
   graph still holds).
7. The per-error `renderTypeError` (the split-out replacement for `renderTypeErrors`) remains the
   single source of the rendered strings — do not fork the rendering.

## In-repo update sites (E1 breaks `BindError`'s shape)

Changing `BindError`'s single field breaks its extractor and construction across this repo. Update
these in the same change; none is a plan deviation:

- `core/src/test/scala/vql/semantics/SatisfyingSetSpec.scala` (~line 114): the pattern
  `case Left(QueryError.BindError(errors))` and its `errors.exists(_.contains("y"))` assertion move to
  the `details`/`messages` shape (e.g. `case Left(e: QueryError.BindError) => assert(e.messages.exists(...))`).
- `core/src/main/scala/vql/semantics/VagueSemantics.scala` (~lines 50 and 110): both
  `QueryError.BindError(errors = renderTypeErrors(errors))` construction sites move to
  `QueryError.BindError(details = errors.map(toBindErrorDetail))` (or equivalent fold).

Per AC #4/§Versioning, this is the one existing assertion the chosen option forces to change — it is
authorized by this section, so update it rather than flagging it separately.

## Versioning & handoff

- This is an external-API change to the public `QueryError` hierarchy. Bump `ThisBuild / version`
  one **MINOR** step under early-semver (`0.14.0` → `0.15.0`) and add a `CHANGELOG.md` entry
  describing the new `QueryError.BindError` shape / new variant and how consumers read the sort.
- register will re-pin to the new version and collapse its two-tier bind-error handling back to a
  single `fromQueryError` mapper (the node-sort classification moving inside that mapper's
  `BindError` case). Keep the contract in "Goal" stable so that collapse is a small register diff.
- Do not weaken or delete any existing engine test to make room for the change; add new tests
  alongside. The `SatisfyingSetSpec` extractor change listed under "In-repo update sites" is the one
  pre-authorized mechanical update (shape change, same assertion intent). If any *other* existing
  assertion is forced to change, flag it as a decision before doing so.
