# Implementation Plan: Bind-Error Sort Fidelity in `QueryError.BindError`

**Status:** DONE (2026-08-17). Phases 0–2 complete; suite green both platforms.
**Date:** 2026-08-17
**Source contract:** `PROMPT-BIND-ERROR-SORT-FIDELITY.md` (repo root) — its
acceptance criteria AC-1 … AC-7 are the interface contract the downstream
**register** project designs against. This plan implements that contract with
**Option E1** (structured detail in the error layer), the option the source
contract recommends and the repo owner is to confirm at Phase 0.
**Parent ADRs:** [ADR-004](ADR-004.md) (layer direction — this change stays
inside it), [ADR-006](ADR-006.md) (`enum` vs `sealed trait` encoding),
[ADR-012](ADR-012.md) (error channels — unchanged), [ADR-015](ADR-015.md)
(symmetric value boundaries — the sort name crosses as the `TypeId.value`
primitive).
**New ADR created by this plan:** [ADR-019](ADR-019.md) (structured bind-error
detail across the error/typed boundary — Proposed at Phase 0, Accepted after
Phase 1). Records the layering decision the T-008 note deferred (§7 Decision #2,
ruled: new ADR).
**Related TODO:** the T-008 note (`docs/TODOS.md`) records the
`BindError` / `ModelValidationError` `List[String]` smell as a "separate,
larger change … it means deciding that dependency direction." This plan
resolves that note for `BindError` by keeping the `vql.error → vql.typed`
direction and crossing the boundary as primitives. `ModelValidationError` is
out of scope.
**Downstream consumer:** register (HTTP status mapping). No register-side
changes in this plan; register re-pins to `0.15.0` and collapses its two-tier
bind-error handling into a single `fromQueryError` mapper afterwards.

---

## 0. Executive Summary

**What changes and why.** register must map two bind-phase outcomes to
different HTTP results — an unknown quoted node name to `UNKNOWN_REFERENCE`,
any genuine type error to `BIND_FAILED` — but today the high-level entry point
`VagueSemantics.evaluateTyped` renders every bind error to a `String` and
discards the sort. register's only workaround is to bypass `evaluateTyped`,
call `QueryBinder.bind` directly, and duplicate the engine-private
`renderTypeErrors`. This plan carries the sort across the layer boundary as a
primitive so register keeps the single `evaluateTyped` call and a single error
mapper.

**How (Option E1).** Add an error-layer `enum BindErrorDetail` (primitives
only) that co-locates each error's sort name with its own rendered message.
`QueryError.BindError` changes from `errors: List[String]` to a single field
`details: List[BindErrorDetail]`, with the message contract derived as
`def messages: List[String] = details.map(_.rendered)`. The engine-private
`renderTypeErrors(List[TypeCheckError])` splits into a per-error
`renderTypeError(TypeCheckError): String` so rendered strings still come from
one place.

**Layer direction is unchanged.** `vql.error` still does not depend on
`vql.typed`. The sort crosses as `sort.value: String`; no `TypeCheckError` or
`TypeId` enters `vql.error`.

| Phase | Output | Halt | Status |
|---|---|---|---|
| 0 | This plan + [ADR-019](ADR-019.md) (Proposed) written; §1 full-corpus ADR review (findings F1/F2); §7 #1/#2/#3 all ruled | HARD STOP | ✅ (docs) |
| 1 | Error layer + facade + in-repo call-site updates + new tests (AC-1 … AC-7); full suite green | HARD STOP | ☐ |
| 2 | Version bump `0.14.0 → 0.15.0`, `CHANGELOG.md` entry, ADR-019 → Accepted, doc-consistency sweep | HARD STOP | ☐ |

> ⚠️ Per WORKING-INSTRUCTIONS, the agent halts after every phase and waits for
> explicit user continuation. All commits and pushes are performed by the user
> personally; the agent prepares the working tree and reports, never commits.
> Every phase ends with the full cross-platform suite green: `sbt test` from
> the repo root (aggregates `folEngine.jvm` and `folEngine.js`). Report
> pass/fail only.

---

## 1. ADR Compliance Review (Planning Phase)

This is a cross-cutting typing/error-layer change, so every active ADR gets an
explicit verdict — not only the ones obviously touched. The active corpus is
ADR-001, 002, 003, 004, 006, 007, 012, 014, 015, 016, 017, 018, and the ADR-00X
meta-template. (Archived ADR-005/008/009/010 are out of scope per
WORKING-INSTRUCTIONS; ADR-019 is the new ADR this plan creates.)

| ADR | Verdict | Basis |
|---|---|---|
| 001 Typed IL binding | Relevant, compliant | Touches the `bindTyped`/`evaluateTyped` facade (§5 canonical entry). `QueryBinder.bind`'s `Either[List[TypeCheckError], BoundQuery]` signature and `BoundQuery` are unchanged; the fold stays downstream of `bind`, where §5 already places it. |
| 002 Parser-combinator style | Not relevant | No parser, `ParseResult`, or lexer code touched. |
| 003 HDR sampling | Not relevant | No sampling code touched. |
| 004 Tagless-initial / layering | **Relevant, primary constraint — compliant** | Preserves the `vql.error → vql.typed` prohibition ([QueryError.scala:128-129](../core/src/main/scala/vql/error/QueryError.scala#L128-L129)). `BindErrorDetail` has only `String` fields; the only `TypeCheckError → BindErrorDetail` conversion runs in the facade, which already imports both. No new import direction. Centralising the fold also matches the "Scattered Type Conversions" smell. |
| 006 `enum` vs `sealed trait` | **Relevant — deviation found and fixed (F1)** | `BindErrorDetail` is a pure-data sum → `enum`; `QueryError.BindError` stays a `case class` variant of the `sealed trait` (variants override `formatted`/`context`). **The original §2 sketch declared a concrete `def rendered` while also naming a `rendered` case parameter — the exact ADR-006 §3 name-clash; it does not compile.** Fixed: `rendered` is now an abstract member the case parameters implement (see §2, F1). |
| 007 OCaml core preservation | Not relevant | No file in ADR-007's scope tables (Tiers 1–3) is touched; `vql.error` and `vql.semantics` are outside that scope. |
| 012 `require` vs `Either` | Relevant, compliant | `BindError` still on the `Either` left; no new `require`/`throw`; `renderTypeError` is a pure helper; the facade composes `Either` directly (matches the ADR-012 "GOOD" fold). |
| 014 Quantifiability | **Relevant — doc staleness found (F2)** | No behaviour change, but ADR-014's `VagueSemantics` implementation row names `renderTypeErrors` ([line 157](ADR-014.md)), and its Code Smell reads `e.errors.exists(...)` ([line 114](ADR-014.md)). Both go stale under this change (`renderTypeErrors → renderTypeError`; `errors → messages`). Added to the Phase 2 sweep (§5, F2). |
| 015 Symmetric value boundaries | Relevant, compliant | The sort crosses as `TypeId.value`, the sanctioned primitive read; no `asInstanceOf`, no new carrier. The `UnparseableConstant(name, sort, sourceText)` shape read by the fold matches ADR-015 §4. |
| 016 Carrier witness | Relevant, no impact | Concerns `LiteralRef` payload typing / the `BoundTerm` split; this change touches neither `BoundTerm` nor `Carrier`. Proposed status unaffected. |
| 017 Formula ranges / satisfying-set | **Relevant — doc staleness found (F2)** | The `satisfyingSet` facade is the second `BindError` construction site (VagueSemantics.scala:110). ADR-017's `VagueSemantics` implementation row names `renderTypeErrors` ([line 250](ADR-017.md)) → stale after the split. Added to the Phase 2 sweep (§5, F2). |
| 018 Structural fragment membership | Not relevant | Fragment/parse-tree code untouched. |
| 00X meta-template | Relevant (governs ADR-019) | [ADR-019](ADR-019.md) follows the ADR-00X structure (Context / Decision / Code Smells / Cross-ADR / Alternatives Rejected / References). |

**Findings from the full-corpus pass** (these are why the review must cover
every ADR, not a hand-picked subset):

- **F1 — ADR-006 §3 name-clash (correctness, fixed).** The `enum
  BindErrorDetail` sketch as first written did not compile: a concrete
  `def rendered` cannot coexist with a `rendered` case parameter. Confirmed by
  compiling the snippet. Resolved in §2 and [ADR-019](ADR-019.md) §Decision 1 by
  making `rendered` an abstract member the case parameters implement. The
  register-facing accessor (`detail.rendered`, `detail.sortName`) is unchanged.
  See Decision #3 (§7) for the abstract-member vs derived-accessor choice.
- **F2 — doc-consistency sweep was incomplete.** Renaming `renderTypeErrors →
  renderTypeError` and `BindError.errors → messages` staled two ADRs the earlier
  subset review did not open: ADR-014 (impl row + Code Smell) and ADR-017 (impl
  row). Both are now in the Phase 2 sweep (§5). Note the deliberate
  **non**-change: `ModelValidationError` keeps `errors: List[String]`, so
  ADR-001's `${e.errors}` Code Smell ([line 177](ADR-001.md)) and
  [QueryError.scala:142](../core/src/main/scala/vql/error/QueryError.scala#L142)
  are left as-is — the sweep must not "fix" them.

**Alignment note:** this plan resolves the T-008 note's open question ("deciding
that dependency direction") in favour of **keeping** the direction and passing
primitives — Option E1, not Option E3. Recorded in [ADR-019](ADR-019.md)
(§7 Decision #2), Proposed at Phase 0, Accepted after Phase 1.

---

## 2. Public API change (the contract register reads)

```scala
package vql.error

/** Per-error bind-phase detail. Primitives only: the error layer must not
  * depend on vql.typed, so the sort crosses as its TypeId.value String.
  * `rendered` is the single human-readable message for this error, produced
  * by VagueSemantics.renderTypeError.
  */
enum BindErrorDetail:
  case UnparseableConstant(name: String, sortName: String, sourceText: String, rendered: String)
  case Other(rendered: String)

  /** `rendered` is a stored value, not derived from other fields, so it is an
    * abstract member the case parameters implement. A concrete
    * pattern-matching `def rendered` would clash with the same-named case
    * parameter and fail to compile (ADR-006 §3 name-clash rule).
    */
  def rendered: String
```

```scala
// QueryError.BindError — single field; message contract derived, no drift.
case class BindError(details: List[BindErrorDetail]) extends QueryError:
  def messages: List[String] = details.map(_.rendered)
  def message = s"Query type-checking failed: ${messages.mkString("; ")}"
  override val context = Map("errors" -> messages.mkString("; "))
```

**Consumer classification rule (register, for reference — not engine code).**
Map to `UNKNOWN_REFERENCE` iff *every* detail is node-recoverable — a
`BindErrorDetail.UnparseableConstant` with `sortName == "Node"`, or the
homogeneous `UnknownConstantOrLiteralError` path (unchanged). Otherwise
`BIND_FAILED` with the rendered text. A mix of an unknown node name and a
genuine type error therefore falls to `BIND_FAILED` — the type error
dominates, so no mixed-case per-error classification is required. A homogeneous
non-node unparseable (a bad `Loss` / `Probability` literal, reachable because
those sorts have validators) stays `BIND_FAILED` and reads its rendered text
from the detail without re-rendering.

---

## 3. Facade fold (`vql.semantics.VagueSemantics`)

Two construction sites and one private renderer change:

- Split
  [`renderTypeErrors(List[TypeCheckError]): List[String]`](../core/src/main/scala/vql/semantics/VagueSemantics.scala#L53)
  into a per-error `renderTypeError(TypeCheckError): String` (same body, one
  error at a time). This is a refactor, not a fork — it stays the single source
  of rendered strings (AC-7).
- Add a private `toBindErrorDetail(TypeCheckError): BindErrorDetail`:
  - `UnparseableConstant(name, sort, src)` →
    `BindErrorDetail.UnparseableConstant(name, sort.value, src, renderTypeError(e))`
  - everything else → `BindErrorDetail.Other(renderTypeError(e))`
- Both current `QueryError.BindError(errors = renderTypeErrors(errors))` sites
  ([line 50](../core/src/main/scala/vql/semantics/VagueSemantics.scala#L50) in
  `bindTyped`,
  [line 110](../core/src/main/scala/vql/semantics/VagueSemantics.scala#L110) in
  `satisfyingSet`) become
  `QueryError.BindError(details = errors.map(toBindErrorDetail))`.
- The `UnknownConstantOrLiteralError` homogeneous branch in `bindTyped`
  (lines 45–48) is unchanged (AC-4).

---

## 4. In-repo update sites (complete list — E1 changes `BindError`'s shape)

Changing `BindError`'s single field breaks its extractor, its field reads, and
its construction across this repo. All sites, from a full `grep`:

| Site | Current | Under E1 | Authorization |
|---|---|---|---|
| [VagueSemantics.scala:50](../core/src/main/scala/vql/semantics/VagueSemantics.scala#L50) | `BindError(errors = renderTypeErrors(errors))` | `BindError(details = errors.map(toBindErrorDetail))` | core change (this plan) |
| [VagueSemantics.scala:110](../core/src/main/scala/vql/semantics/VagueSemantics.scala#L110) | same construction | same | core change (this plan) |
| [SatisfyingSetSpec.scala:114](../core/src/test/scala/vql/semantics/SatisfyingSetSpec.scala#L114) | `case Left(QueryError.BindError(errors)) => errors.exists(_.contains("y"))` | `case Left(e: QueryError.BindError) => e.messages.exists(_.contains("y"))` | pre-authorized mechanical (source-contract §"In-repo update sites") |
| [VagueSemanticsTypedSpec.scala:142-144](../core/src/test/scala/vql/semantics/VagueSemanticsTypedSpec.scala#L142-L144) | `e.errors.nonEmpty` / `e.errors.exists(...)` | `e.messages.nonEmpty` / `e.messages.exists(...)` | authorized by ruling (§7 Decision #1 → A, mechanical rename) |
| [VagueSemanticsTypedSpec.scala:170](../core/src/test/scala/vql/semantics/VagueSemanticsTypedSpec.scala#L170) | `case Left(_: QueryError.BindError) => assert(true)` | unchanged (wildcard) | no change needed |

Scaladoc reference `[[BindError]]` at
[QueryError.scala:119](../core/src/main/scala/vql/error/QueryError.scala#L119)
is prose, not code — swept in Phase 2.

---

## 5. Phases

### Phase 0 — Plan + decisions (documents only) ✅
This document and [ADR-019](ADR-019.md) (Proposed). §7 Decisions ruled: Option
E1 confirmed; #1 → A (authorize the mechanical `errors → messages` rename); #2 →
A (record the layering decision as ADR-019). No code.
**HARD STOP.**

### Phase 1 — Error layer, facade, call sites, tests ✅
1. Add `enum BindErrorDetail` to `vql.error` (§2).
2. Change `QueryError.BindError` to `details` + derived `messages`/`message`/
   `context` (§2); update its scaladoc to describe the structured shape and the
   primitives-only layering reason.
3. Facade: split `renderTypeErrors` → `renderTypeError`, add
   `toBindErrorDetail`, update both construction sites (§3).
4. Update the in-repo test sites per §4 — including the authorized
   `errors → messages` rename in `VagueSemanticsTypedSpec` (§7 Decision #1 → A).
5. New tests: AC-1/AC-7, AC-3, AC-5, AC-6 (§6). AC-2 has no engine test
   (§7 Decision #4 → B). AC-4 is existing coverage. Do not weaken or delete
   existing tests; add alongside.
6. Full suite green: `sbt test` (799/799 both platforms).

### Phase 2 — Versioning, changelog, ADR acceptance, handover, doc sweep ✅
1. `ThisBuild / version := "0.15.0"` in `build.sbt`.
2. `CHANGELOG.md` entry: new `BindError` shape, `BindErrorDetail`, how a
   consumer reads the sort (`sortName`), message contract preserved via
   `messages`.
3. [ADR-019](ADR-019.md) status Proposed → Accepted, add acceptance date;
   add it to the WORKING-INSTRUCTIONS validation set.
4. Handover section for register (§9): the `BindError`/`BindErrorDetail` shape,
   how register reads `sortName`/`rendered`, the node-recoverability
   classification rule, and the single-error-today caveat with the accumulation
   follow-up.
5. Doc-consistency sweep (full list from the F2 finding, §1):
   - `[[BindError]]` scaladoc at QueryError.scala and the layering comment
     (now `details`, not rendered `errors`).
   - **ADR-014**: implementation row `renderTypeErrors → renderTypeError`;
     Code Smell `e.errors.exists(...) → e.messages.exists(...)`.
   - **ADR-017**: implementation row `renderTypeErrors → renderTypeError`.
   - T-008 note in `docs/TODOS.md`: mark the `BindError` `List[String]` smell
     resolved for `BindError`; state `ModelValidationError` stays out of scope.
   - Any `README.md` / `VagueQuantifiers.md` mention of `BindError`'s shape
     (verify with `grep`).
   - **Do not touch** `ModelValidationError` or its ADR-001:177 Code Smell —
     that half keeps `errors: List[String]`.
6. Full suite green.

---

## 6. Acceptance criteria → test mapping

| AC | Assertion | New test |
|---|---|---|
| 1 | Sole `UnparseableConstant` bind error: sort name recoverable from the `QueryError` returned by `evaluateTyped` (not `QueryBinder.bind`) | detail is `UnparseableConstant` with expected `sortName` |
| 2 | Mixed-list classification (`UNKNOWN_REFERENCE` only if *every* detail is a Node typo): **no engine test in this plan — ruled B (§7 Decision #4).** At ruling time the binder was single-error, so a mixed list was unreachable through the pipeline; the engine's obligation — each detail exposes its `sortName` — is covered by AC-1/AC-3, and register owns the all-node predicate and tests it. The mixed list is now produced and pipeline-tested in PLAN-binder-error-accumulation (0.16.0, ADR-020). | (none) |
| 3 | Homogeneous non-node `UnparseableConstant` (bad `Loss` literal): recoverable as a structured detail carrying **both** sort name and rendered message; consumer classifies `BIND_FAILED` without re-rendering | detail has `sortName == "Loss"` and non-empty `rendered` |
| 4 | `UnknownConstantOrLiteralError` homogeneous path unchanged | existing tests stay green (no new-code path taken) |
| 5 | Rendered messages for `BindError` unchanged for a string-only consumer | `e.messages` equals the pre-change `renderTypeErrors` output for the same query |
| 6 | No compile dependency `vql.error → vql.typed` | `BindErrorDetail` has no `vql.typed` import; package graph holds (compilation is the check) |
| 7 | `renderTypeError` is the single source of rendered strings (rendering not forked) | facade uses `renderTypeError` in both the detail's `rendered` and any message path |

Reachable-sort inputs for AC-1/AC-3 (from the source contract):
`gt_loss(p95(x), "abc")` → `UnparseableConstant("abc", sort = Loss, …)`;
`gt_prob(lec(x,100), "2.0")` → `UnparseableConstant("2.0", sort = Probability, …)`.

---

## 7. Decisions

#1–#4 all ruled 2026-08-17. No decision is open. Phases 1 and 2 executed.

**Decision #1 — the forced test change in `VagueSemanticsTypedSpec` — ruled A.**
[VagueSemanticsTypedSpec.scala:142-144](../core/src/test/scala/vql/semantics/VagueSemanticsTypedSpec.scala#L142-L144)
reads `e.errors.nonEmpty` / `e.errors.exists(...)`, which E1 removes. The source
contract's "In-repo update sites" pre-authorized only the `SatisfyingSetSpec`
extractor, so this site required an explicit ruling. **Ruled: authorize the
mechanical `errors → messages` rename** — same assertion intent, one derived
field name changed. Applied in Phase 1 (§4, §5 step 4).

**Decision #2 — where the layering decision is recorded — ruled A.** E1 settles
the `vql.error → vql.typed` direction the T-008 note deferred. **Ruled: record
it as a new ADR in the ADR-00X format** → [ADR-019](ADR-019.md), Proposed at
Phase 0, Accepted after Phase 1 (§5 Phase 2 step 3).

**Decision #3 — `BindErrorDetail.rendered` encoding — ruled A (2026-08-17).**
The first sketch did not compile (ADR-006 §3 clash). Two compliant forms exist;
both expose the same `detail.rendered` / `detail.sortName` accessors register
reads, both compile (verified):
- **A (ruled): abstract member.** `rendered` stays a case parameter; the enum
  declares `def rendered: String` abstract, each case implements it. Keeps the
  field name the source contract used; no dead code. `rendered` is stored, not
  derived, so ADR-006 §3 (which governs *derived* pattern-matching accessors)
  does not literally apply.
- **B (not chosen): strict ADR-006 §3.** Rename the stored field to `message`;
  derive `def rendered = this match …`. Exactly ADR-006 §3's `RuntimeModelError`
  shape, but `rendered` is a trivial pass-through of `message` (mild dead-code
  feel).

**Ruled: A** — `rendered` is a stored value, not a derivation, so the
abstract-member idiom fits; §3's prohibition targets the concrete-`def` clash,
which A avoids. The docs (PLAN §2, ADR-019 §Decision 1) already show A. The
ADR-006 §3 clarification (that the rule is about *derived* accessors, not
abstract members implemented by case parameters) is applied.

**Decision #4 — how AC-2 (mixed-list classification) is verified — ruled B
(2026-08-17).** The binder short-circuits (every combinator is a `for`/`foldLeft`
over `Either`, each `Left` a singleton list), so a mixed bind-error list is
unreachable through the pipeline; a real query never yields more than one error.
- **A (not chosen): direct-construction stand-in.** An engine test hand-builds a
  mixed `BindError` and runs a copy of register's "every detail node-recoverable"
  predicate over it. Documents the mixed case now, but puts register-owned
  classification logic in the engine suite.
- **B (ruled): no engine test for the mix.** The engine's obligation — each
  detail carries its `sortName` and `rendered` — is covered by AC-1/AC-3/AC-5.
  register owns the all-node predicate and tests it. The mixed-list case is now a
  real pipeline test (AC-A/AC-B) in PLAN-binder-error-accumulation (0.16.0,
  ADR-020), where the binder emits mixed lists.

**Ruled: B** — the engine exposes the data; the classification is the consumer's,
and duplicating it in the engine suite tests logic the engine does not own. The
handover to register (§9) states the classification rule so the consumer has it
in writing.

## Follow-up workstream (separate plan, approved 2026-08-17)

Binder error accumulation: rework `QueryBinder` to collect errors across
independent subtrees (conjuncts, argument lists) while still sequencing
range → scope (the environment threads through that dependency). Makes the
`List[TypeCheckError]` return type honest, aligns the binder with
`TypeCatalog.collectErrors` (which already accumulates), and turns AC-2 into a
real pipeline test. Requires its own ADR fixing the accumulate-vs-sequence
boundary and the cascade-error policy. Opened as a new plan after this workstream
ships — not appended here.

---

## 8. Versioning & handoff

- External-API change to the public `QueryError` hierarchy. Bump one MINOR step
  under early-semver: `0.14.0 → 0.15.0` (0.14.0 is published). `CHANGELOG.md`
  entry per Phase 2.
- register re-pins to `0.15.0` and collapses its two-tier bind handling into a
  single `fromQueryError` mapper, moving the node-sort classification inside
  that mapper's `BindError` case. The §2 contract is kept stable so that
  collapse is a small register diff.
- No existing engine test is weakened or deleted; new tests are added
  alongside. The two test-site edits in §4 are the only forced changes, both
  mechanical field renames (§7 Decision #1 covers the one the source contract
  did not pre-authorize).

---

## 9. Handover to register

register consumes this change. This is the contract it reads and the
classification it owns.

**What the engine now returns.** A bind failure is `QueryError.BindError` with a
single field:

```scala
case class BindError(details: List[BindErrorDetail])
```

Each `BindErrorDetail` is one of:

```scala
enum BindErrorDetail:
  case UnparseableConstant(name: String, sortName: String, sourceText: String, rendered: String)
  case Other(rendered: String)
  def rendered: String   // the human-readable message for this error
```

- `sortName` is the sort the failed token was expected to be, as a plain
  `String` (`TypeId.value`). This is the detail register needs: it is what tells
  a mistyped entity reference from a genuine type error.
- `rendered` is the engine's message for that error. Read it directly — do not
  re-derive it. `BindError.messages: List[String]` is `details.map(_.rendered)`
  for a text-only consumer.

**The classification register owns.** Map a `BindError` to `UNKNOWN_REFERENCE`
(a user-recoverable reference mistake) only if *every* detail is
node-recoverable:

```scala
def nodeRecoverable(d: BindErrorDetail): Boolean = d match
  case BindErrorDetail.UnparseableConstant(_, sortName, _, _) => sortName == "Node"
  case BindErrorDetail.Other(_)                               => false

val outcome =
  if bindError.details.forall(nodeRecoverable) then UNKNOWN_REFERENCE
  else BIND_FAILED   // read bindError.messages for the text
```

The homogeneous `UnknownConstantOrLiteralError` path (a token that is neither a
constant nor has a literal validator) is unchanged and still maps to
`UNKNOWN_REFERENCE` directly.

**Multi-error list.** As of 0.16.0 the binder accumulates errors across
independent subtrees (ADR-020), so `details` can hold several entries — for
example `big(x, "abc") /\ leaf(x, x)` yields two. The `forall` above is written
for exactly this: a list that mixes any non-node error falls to `BIND_FAILED`,
which is the intended behaviour; a list whose every entry is a node-recoverable
unparseable constant is `UNKNOWN_REFERENCE`. register needs no code change for
the multi-element list beyond re-pinning.

**Migration.** register re-pins to `0.16.0` and collapses its two-tier bind
handling — it no longer drops to `QueryBinder.bind` to recover the sort — into a
single `fromQueryError` mapper whose `BindError` case runs the classification
above.
