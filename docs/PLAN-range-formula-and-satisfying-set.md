# Implementation Plan: Untyped-Path Retirement + Formula Ranges + Satisfying-Set API

**Status:** Draft — awaiting user review (per `docs/WORKING-INSTRUCTIONS.md`
§ "Mandatory Review Halt"). All rulings recorded (§2); awaiting the user's
signal to begin Phase 0.
**Date:** 2026-08-10
**Source contract:** `PROMPT-VQL-RANGE-AND-TARGETING.md` (repo root) — the
acceptance criteria AC-1 … AC-10 defined there are the interface contract the
downstream register project designs against. This plan implements that
contract with one user-authorized deviation: AC-9 ("untyped path untouched")
is superseded by the ruling of 2026-08-10 to retire the untyped backend as
Phase 0 (resolves `docs/TODOS.md` T-006).
**Parent ADRs:** [ADR-001](ADR-001.md) (typed IL — amended by this plan),
[ADR-002](ADR-002.md), [ADR-007](ADR-007.md), [ADR-012](ADR-012.md),
[ADR-014](ADR-014.md), [ADR-015](ADR-015.md). New ADR-017 is created by this
plan (Ruling of 2026-08-10, recorded in §2).
**ADRs deprecated by Phase 0:** ADR-005, ADR-008, ADR-009, ADR-010.
**Related TODOs:** T-002 (named-constant eval interim), T-006 (untyped
backend retirement — executed here).
**Downstream consumer:** register (mitigation targeting + sub-population
analytics). No register-side changes in this plan.

---

## 0. Executive Summary

Three pieces of work, in dependency order:

- **Phase 0 — retire the untyped evaluation backend** (T-006, ruled
  2026-08-10). The typed path (`VagueSemantics.evaluateTyped` →
  `fol.typed`) is the only production path; the untyped machinery
  (`RangeExtractor`, untyped `holds`/`evaluate`, bridge, datastore,
  `ResolvedQuery`) has no consumer outside this repo's demos and tests.
  Removing it first means the range widening (Phase 3) touches exactly one
  backend.
- **Formula ranges (Jobs 1).** The range of a vague query widens from a
  single positive atom to a full formula. Semantics:
  `D_R = { d ∈ domain(sort(x)) | evalFormula(range, env + (x→d)) }`.
  Negation is closed-world complement over the finite active domain;
  conjunction is intersection. The proportion's denominator becomes the
  compound population (AC-2).
- **Satisfying-set API (Job 2).** A public entry point that evaluates a bare
  formula (one free variable, no quantifier, no answer variables) to its
  exact satisfying set over that variable's sort domain.

| Phase | Output | Halt |
|---|---|---|
| 0 | Untyped-path retirement: deletions per inventory (§4.1) incl. the untyped demos, ADR status sweep (ADR-005/008/009/010 → Deprecated), full markdown-corpus sweep per disposition table (§4.2) incl. README rewrite | HARD STOP |
| 1 | FOL parse boundary: foundation-local parse-error type; `FOLParser.parse`/`defaultParser`/`parseWithLexer` return `Either`; call-site updates; ADR-007 deviation note; ADR-012 §Relationship finalized | HARD STOP |
| 2 | ADR-017 draft (formula-range semantics + satisfying-set boundary) + consistency review; ADR-001 §3 edit staged | HARD STOP |
| 3 | Typed IL widening: `BoundQuery.range: BoundFormula`; `collectRangeElements` via `evalFormula`; IR-level compound-range tests (AC-1, AC-2 at IR level) | HARD STOP |
| 4 | Surface widening: `ParsedQuery.range: Formula[FOL]`, parser range production, binder reuses `bindFormula`; AC-1/2/3/4/6 tests | HARD STOP |
| 5 | `satisfyingSet` entry point (accepts pre-parsed `Formula[FOL]`); AC-5 tests | HARD STOP |
| 6 | Docs (AC-8), ADR-001 consistency edit, ADR-017 acceptance, version bump (AC-10), doc-consistency sweep | HARD STOP |

> ⚠️ **Per WORKING-INSTRUCTIONS § Mandatory Review Halt**, the agent halts
> after every phase and waits for explicit user continuation.
> All commits and pushes are performed by the user personally; the agent
> prepares the working tree and reports, never commits.
> The file deletions in Phase 0 are pre-authorized by user approval of this
> plan, which lists them explicitly (§4.1), satisfying WORKING-INSTRUCTIONS
> § CRITICAL STOP POINTS.

Every phase ends with the full cross-platform suite green:
`sbt test` from the repo root (aggregates `folEngine.jvm` and `folEngine.js`).
Report pass/fail only. Baseline before Phase 0: pass (verified 2026-08-10).

---

## 1. ADR Compliance Review (Planning Phase)

**Reviewed:** ADR-001, ADR-002, ADR-003, ADR-004, ADR-005, ADR-006, ADR-007,
ADR-008, ADR-009, ADR-010, ADR-012, ADR-014, ADR-015, ADR-016, ADR-00X.

**Deviations detected:**

1. **ADR-001 §3** codifies the typed IL with `range: BoundAtom`
   ("sort-checked range predicate") and shows `ParsedQuery` feeding it.
   Ruled 2026-08-10: a new ADR-017 carries the formula-range semantics and
   the satisfying-set boundary; ADR-001's snippet is updated for consistency
   in Phase 5 with a cross-reference.
2. **ADR-005, ADR-008, ADR-009, ADR-010** govern machinery deleted by
   Phase 0 (model augmentation, generic `KnowledgeBase[D]`, symmetric
   relations, `RelationName`). They move to **Deprecated** in Phase 0.
   The type-safety principles they express survive where re-hosted: the
   `ClassTag` sampling constraint lives in ADR-003's scope; the typed-
   identifier principle lives on in the typed path's opaque types
   (`TypeId`, `SymbolName`, ADR-001).
3. **ADR-012 § Relationship to ADR-007** states that "public boundaries in
   those files already convert to Either (e.g., `FOLParser.parse`,
   `RangeExtractor.extractRange`)". This is factually wrong for
   `FOLParser.parse`, which returns `Formula[FOL]` and throws. The
   `RangeExtractor` half of the example disappears with Phase 0's
   retirement; Phase 1 then makes the `FOLParser.parse` half true as
   written (Ruling of 2026-08-10) and finalizes the section's wording.

**Alignment notes (cross-cutting dependencies found in the review):**

- **ADR-002 / ADR-007 (parser).** The range production change lives entirely
  in `VagueQueryParser` (vague layer, ADR-002 style). It reuses
  `FormulaParser.parse(parseInfixAtom, parseAtom)` — the exact combinator
  stack the scope already uses. No file in ADR-007's scope tables (Tiers
  1–3) is modified; characteristics C1–C13 are untouched. Grammar
  termination is sound without changes to the OCaml core: `,` is not an
  infix operator, so the formula parser stops before the range/scope comma;
  commas inside atom argument lists are consumed within `parseAtom`'s
  parentheses. A test pins this.
- **ADR-001 / binder reuse.** `QueryBinder.bindFormula` already exists for
  the scope and already implements cross-atom sort unification via
  `mergeEnvs`, returning `TypeCheckError.ConflictingTypes` on disagreement.
  Range binding becomes a call to it: AC-4 is satisfied by existing
  machinery.
- **ADR-014 (quantifiability).** The quantified variable's domain-type check
  (`TypeNotQuantifiable`) already runs in `bind` after range binding, and
  `bindQuantified` applies the same check to every inner `∀`/`∃` variable.
  Formula ranges inherit both checks with no new code. `satisfyingSet` must
  replicate the check explicitly for its variable, because it enumerates
  that variable's domain outside `bind`.
- **ADR-012 / ADR-002 / ADR-004 (error channels and layering).** All new
  vague-layer surface returns `Either[QueryError, A]`. `QueryError` stays
  in the vague layer, which the foundation must not import (ADR-004: FOL
  never imports vague). Ruled 2026-08-10: no shared error package — the
  foundation keeps its standalone reusability and gets its own
  foundation-local parse-error type for the Phase 1 boundary; consumers
  needing `QueryError` map the foundation error at their own boundary.
- **ADR-003 (sampling).** Range extraction stays exhaustive; sampling
  continues to apply only in `evaluateOverRange`. AC-6 pins this with a
  test. Closed-world negation is only sound because range extraction is
  exact full enumeration — stated as an invariant in ADR-017. ADR-003's
  implementation table row pointing at `ResolvedQuery.evaluate()` is
  redirected to the typed pipeline in Phase 0.
- **ADR-015 / ADR-016 (literals and constants).** Range formulas may contain
  inline literals, named constants, and function applications; all flow
  through the existing `LiteralRef` / `ConstRef` / `FnApp` binder branches
  and `Extract[A]` boundaries unchanged. Range extraction can now invoke
  dispatcher functions (previously only the single range predicate was
  dispatched); the T-002 interim for `ConstRef` evaluation applies inside
  ranges exactly as in scopes — documented in ADR-017, no new work.
- **ADR-006 (ADT encoding).** No new sum types; the range reuses the
  existing `BoundFormula` enum. The one new error variant follows the
  existing `TypeCheckError` encoding.

---

## 2. Rulings

Recorded decisions:

- **Ruling of 2026-08-10 — design authority:** new **ADR-017** (formula-range
  semantics, closed-world negation over the active domain, satisfying-set
  boundary) plus a consistency edit to ADR-001 §3. (Was open decision 1.)
- **Ruling of 2026-08-10 — untyped path:** retire the untyped backend as
  Phase 0 of this plan (resolves T-006; supersedes the source contract's
  AC-9 and the former open decision on compile accommodation).
- **Ruling of 2026-08-10 — demos and docs:** the untyped demos
  (`examples/VagueSemanticsDemo.scala`, `fol/examples/CyberSecurityDomain`,
  `CyberSecurityExamples`, `VagueQuantifierDemo`) are deleted in Phase 0;
  no port. A TODO for a fresh typed-path demo (written after Phase 5,
  against the finished API) is recorded in `docs/TODOS.md` during Phase 0.
  The Phase 0 documentation sweep covers the entire markdown corpus per the
  disposition table in §4.2, including a README rewrite (its quick-start
  example uses the untyped path).

- **Ruling of 2026-08-10 — formula-parse boundary and `satisfyingSet`
  input:** no shared error package — the foundation stays a standalone
  usable core (ADR-004) and `QueryError` stays in the vague layer,
  colocated with the vague query machinery. The missing boundary
  conversion is implemented at the foundation entry itself: a new
  foundation-local parse-error type; `FOLParser.parse` / `defaultParser` /
  `parseWithLexer` return `Either`, so only the `Either` form is
  consumer-facing. Executed early, as Phase 1. (Phase 1 is independent of
  Phase 0; it is sequenced after retirement per the earlier first-step
  ruling and can be swapped ahead on request.) `satisfyingSet` accepts a
  pre-parsed `Formula[FOL]`; consumers needing `QueryError` (register at
  its HTTP boundary) map the foundation parse error with a one-line
  `.left.map` — no vague-layer parse wrapper is added.

- **Ruling of 2026-08-10 — external-API `Either` audit required:** the plan
  must prove the entire external-facing surface is `Either`-based and that no
  vague-layer code relies on the exception-based form. Added as the §10.1
  audit (Phase 6) and a §3 hard constraint. Grounded against register's actual
  call sites: register reaches the library only through `Either`-returning
  entries (`VagueQueryParser.parse`, `evaluateTyped`), never through a
  throwing one.

- **Ruling of 2026-08-10 — error-channel policy (refines ADR-012):**
  1. The Harrison OCaml-ported core (ADR-007 Tiers 1–3) keeps its ported
     style, including internal exception backtracking.
  2. All other vague-layer code uses `Either` internally, not throwing.
  3. `Either` is enforced at boundaries: the FOL→vague boundary is `Either`
     (vague code calls only `Either`-returning FOL entries — Phase 1 makes
     `FOLParser` such an entry); vague internals are `Either`-only; FOL code
     that is *not* Harrison-ported is decided per individual ruling.

  Consequences already satisfied by the plan: the foundation `FOLParser` edge
  becomes `Either` (Phase 1); `VagueSemantics`/`fol.typed` are `Either`
  throughout once Phase 0 removes `holds`/`evaluate[D]`; `TypeCatalog.unsafe`
  remains the ADR-012 construction-invariant / programming-error channel with
  its `Either` sibling `apply` (covered by "use `Either` elsewhere", not a
  violation).

- **Ruling of 2026-08-10 — D-1: `VagueQueryParser`/`mk` error style.**
  `VagueQueryParser` (and its helper `ParsedQuery.mk`) keeps its
  throw-internally / convert-at-the-edge style; ADR-002 grandfathers it. Not
  merely for consistency: `VagueQueryParser` composes with the ADR-007-frozen
  foundation parsers, which select grammar alternatives by exception
  backtracking; an `Either`-internal vague parser would have to catch and
  convert the foundation's exceptions at every call, the exact "Re-wrapping
  Exceptions Mid-Flight" smell ADR-002 rejects. `mk`'s only caller is
  `VagueQueryParser.parseTokens`, behind `parse`'s `Either` boundary; register
  never calls `mk`. ADR-002 Context and a new Cross-ADR Relationship section
  (with ADR-007) record this. The §10.1 audit keeps these sites on the
  allowlist.

- **Ruling of 2026-08-10 — Phase 0 scaladoc cleanup in preserved files.**
  A comment-only staleness fix inside an ADR-007-preserved file that follows
  directly from a session-ruled deletion is approved as part of that deletion,
  provided it keeps the file consistent and breaks no C1–C13 characteristic.
  Applied in Phase 0 to `semantics/FOLSemantics.scala` (removed a scaladoc
  paragraph on `integerModel` referencing the deleted `NumericAugmenter`).

- **Ruling of 2026-08-10 — Phase 0 residual ADR illustrations.** The
  Decision/Code-Smells example blocks in ADR-003 (`resolved.evaluate()`),
  ADR-004 (`holdsVague`/`KnowledgeBase`, `RelationValue`/`DomainElement`), and
  ADR-012 (`RelationName`/`KnowledgeBase`/`RangeExtractor`/`toResolved`) that
  still reference retired types stay for now (they teach their principle with
  historical vehicles) and are refreshed in the Phase 6 doc-consistency sweep
  (§10).

No rulings are pending.

---

## 3. Scope and Constraints

**In scope:**
- Deletion of the untyped evaluation backend per the Phase 0 inventory
  (§4.1), including its tests, with ADR status and doc updates.
- `core/src/main/scala/fol/logic/ParsedQuery.scala` — range widening,
  validation re-specification.
- `core/src/main/scala/fol/parser/VagueQueryParser.scala` — range
  production.
- `core/src/main/scala/fol/typed/{BoundQuery, QueryBinder,
  TypedSemantics}.scala` — IL widening, formula-range binding and
  evaluation, satisfying-set evaluation.
- `core/src/main/scala/parser/FOLParser.scala` + a new foundation-local
  parse-error type — string-input entries return `Either` (Phase 1,
  approved ADR-007 deviation); mechanical call-site updates in
  `examples/FOLDemo.scala` and the four foundation test suites.
- `core/src/main/scala/fol/semantics/VagueSemantics.scala` — untyped
  entry-point removal (Phase 0); `satisfyingSet` facade (accepts a
  pre-parsed `Formula[FOL]`).
- `core/src/main/scala/fol/typed/TypeCheckError.scala` — one new variant for
  Job 2's free-variable validation; `renderTypeErrors` arm.
- `core/src/main/scala/fol/error/QueryError.scala` — pruning of variants
  raised only by deleted code (Phase 0, after checking register's consumed
  error surface — see §4.1).
- Docs: new ADR-017; ADR-001 §3 snippet; Deprecated markers on
  ADR-005/008/009/010; consistency edits to ADR-003, ADR-004, ADR-012;
  `VagueQuantifiers.md`; `Architecture.md`; `README.md`; `docs/TODOS.md`
  (T-006 closure note); version bump.

**Out of scope:**
- Changes to the OCaml-ported foundation layer (ADR-007 Tiers 1–3) beyond
  Phase 1's entry-point boundary conversion in `FOLParser` — the combinator
  core, lexer, and all internal exception backtracking are untouched. The
  augmentation combinators inside `semantics/FOLSemantics.scala`
  (`withFunctions`, `withPredicates`, `withFunctionFallback`) are left in
  place even though their consumers are deleted — they sit in an
  ADR-007-preserved file; noted in the Phase 0 report as accepted orphaned
  surface.
- `fol.*` → `vql.*` package rename (T-000).
- Register-side changes.
- `Carrier[A]` GADT (T-005); named-constant `evalConstant` (T-002 interim
  stands).
- Sampled-semantics changes beyond the AC-6 pin.

**Hard constraints:**
- Every phase ends with `sbt test` green (JVM + Scala.js via root
  aggregate).
- AC-3: every previously valid query parses and evaluates identically
  through the typed path; single-atom ranges produce byte-identical
  results.
- No `asInstanceOf` outside the boundaries ADR-015 sanctions.
- **Either-based external API (verified, not assumed).** Every external-facing
  entry point whose failure is data- or input-dependent returns
  `Either[QueryError, A]` (or the foundation parse-error Either for
  `FOLParser`). No vague-layer main source relies on the exception-based
  form of any API: no `catch QueryException` trampoline and no call to the
  throwing `FOLParser` form survives. The only sanctioned throwing surface is
  the ADR-012 construction-invariant / programming-error channel, each member
  of which has an `Either` sibling (see the allowlist in §10.1). This is
  enforced by the audit in §10.1, run at Phase 6 when the full surface exists.
- TDD: failing tests first, then implementation, per phase (Phase 0 is
  deletion-led; its test obligation is the surviving suite green plus the
  removal checks in §4.2).
- User performs all commits/pushes.

---

## 4. Phase 0 — Untyped-Path Retirement (T-006)

**Goal:** Remove the untyped evaluation backend so the engine has one
evaluation path. No behavioral change to the typed path.

### 4.1 Deletion inventory (pre-authorized by plan approval)

Main sources:

| Delete | Content |
|---|---|
| `fol/semantics/RangeExtractor.scala` | untyped range extraction |
| `fol/semantics/DomainExtraction.scala` | KB domain extraction |
| `fol/semantics/ScopeEvaluator.scala` | untyped scope evaluation |
| `fol/semantics/EvaluationContext.scala` | model+valuation bundle (verify no surviving consumer at execution time) |
| `fol/query/ResolvedQuery.scala` | untyped evaluation IL |
| `fol/bridge/` (all 7 files) | FOLBridge, KB→Model converters, augmenters |
| `fol/datastore/` (all 8 files) | KnowledgeBase, KnowledgeSource, Relation, RelationName, DomainCodec, DomainElement, RelationValueValidation, RiskDomain |
| `semantics/ModelAugmenter.scala` | augmenter wrapper (all consumers deleted) |
| `examples/VagueSemanticsDemo.scala`, `fol/examples/` (all 3 files) | untyped demos (ruled 2026-08-10: delete, no port) |

Partial edits:

- `fol/semantics/VagueSemantics.scala`: delete `holds`, `evaluate`,
  `toResolved` and the imports they pull (`KnowledgeSource`,
  `DomainElement`, `DomainCodec`, `FOLBridge`, `ResolvedQuery`,
  `ModelAugmenter`, `ClassTag`); the typed facade (`bindTyped`,
  `evaluateTyped`, error rendering) remains; scaladoc rewritten to describe
  the typed facade only.
- `fol/error/QueryError.scala`: delete variants raised only by deleted code
  (candidate: `RelationNotFoundError`; the exact list is produced during the
  phase by grepping surviving raisers). Before deleting any variant, check
  register's consumed error surface in the sibling checkout (read-only:
  `grep -rn "QueryError\." ../register/modules`) — a variant register
  matches on is kept only if a surviving code path can still raise it;
  otherwise its removal is listed in the phase report as a breaking change
  for the changelog (AC-10).
- `fol/sampling/ProportionEstimator.scala`: if `estimateWithSampling` has no
  surviving caller, delete it (its consumer was `ResolvedQuery`);
  `estimateFromCount` and the rest stay (typed path).

Tests deleted: `fol/bridge/NumericAugmenterSpec`, `fol/datastore/*Spec` (4
files, incl. the symmetric-relation specs), `fol/query/ResolvedQuerySpec`,
`fol/semantics/{EvaluationContextSpec, ModelAugmentationIntegrationSpec,
RangeExtractorSpec, ScopeEvaluatorSpec, VagueSemanticsSpec}`.
`VagueSemanticsTypedSpec` stays. `fol/TestFixtures.scala` pruned to what
surviving specs use.

Kept untouched: the entire foundation layer (`logic/`, `parser/`, `lexer/`,
`semantics/FOLSemantics.scala`, `printer/`, `util/`) per ADR-007, including
`examples/FOLDemo.scala`; the typed path (`fol/typed`, `fol/sampling`,
`fol/result`, `fol/quantifier`, `fol/logic`, `fol/parser`).

### 4.2 Markdown-corpus sweep and ADR status changes (same phase)

Ruled 2026-08-10: the documentation sweep covers every `.md` file in the
repository. Disposition principle: documents describing **current state**
are updated to the post-retirement state; **plan documents, design records,
and review records** keep their content (provenance lives in git and the
plan documents), receiving at most a status-line change.

Updated in this phase:

| File | Change |
|---|---|
| `README.md` | Rewrite: quick-start example currently uses the untyped path (`KnowledgeSource`, `CyberSecurityDomain`, `VagueSemantics.holds`) — replaced with the typed pipeline (`TypeCatalog` → `FolModel` → `evaluateTyped`); layout section drops datastore/bridge/`ResolvedQuery`/untyped entries; the `runMain fol.examples.demo` instruction removed (demos deleted) |
| `docs/ADR-005.md`, `ADR-008.md`, `ADR-009.md`, `ADR-010.md` | `Status: Deprecated (2026-08-XX)` — one-line note that the governed machinery was removed with the untyped backend (T-006) and where surviving principles live (ADR-003 for the `ClassTag` sampling constraint; ADR-001 for typed identifiers) |
| `docs/ADR-003.md` | Implementation row `fol/query/ResolvedQuery.evaluate()` → `fol/typed/TypedSemantics.evaluateOverRange` |
| `docs/ADR-004.md` | §2 layer diagram and §3/§4 (bridge, `EvaluationContext`) redrawn to the surviving structure (foundation + typed vague layer) |
| `docs/ADR-012.md` | Implementation-table rows for `KnowledgeBase`/`KnowledgeSource` removed; the `RangeExtractor` mention in § Relationship to ADR-007 removed (the `FOLParser.parse` wording is finalized in Phase 1, when the claim becomes true) |
| `docs/VagueQuantifiers.md` | Paper-to-code mapping and algorithm tables redirected from `RangeExtractor`/`FOLBridge`/`ResolvedQuery` to the typed pipeline; "Availability" note about `ModelAugmenter` replaced by the dispatcher-based description |
| `docs/Architecture.md` | Swept for deleted modules; describes the two surviving layers |
| `docs/WORKING-INSTRUCTIONS.md` | Factual correction only: § Validation Requirements currently lists another project's ADR corpus (Iron types, logging, ADR-004a/b proposals, ADR-011 — none exist in this repo); replaced with this repo's actual accepted ADRs. Protocol sections unchanged. Flagged for explicit user attention at the phase halt (governance document) |
| `docs/TODOS.md` | T-006 marked DONE; new TODO recorded: fresh typed-path demo, written after Phase 5 against the finished API (per Ruling of 2026-08-10) |

Left as records (content unchanged):

| File | Reason |
|---|---|
| `IMPLEMENTATION_PLAN.md` | Historical build plan of the original implementation |
| `docs/PLAN-symmetric-value-boundaries.md` | Completed-workstream plan record |
| `docs/MULTI-SORTED-TYPE-SYSTEM-V2.md` + `-DECISION-SHEET.md` | Design records preceding ADR-001/014 |
| `PROMPT-VQL-RANGE-AND-TARGETING.md` | Source contract; AC-9 supersession is recorded in this plan |
| `docs/PROMPT-CODE-QUALITY-REVIEW.md` | Procedure doc; no untyped references |
| `docs/TECHNICAL-DEBT.md` | TD-001 is a closed-debt record |
| `docs/scratch/*` | Review records |
| `docs/RELEASE.md`, `LICENSE.md` | No untyped references; release flow unchanged |

**Pass criterion:** `sbt test` green on both platforms; grep produces zero
references to deleted types (`KnowledgeSource`, `RangeExtractor`,
`ResolvedQuery`, `ModelAugmenter`, `RelationName`, `DomainCodec`,
`DomainElement`) outside git history; phase report lists every deleted file,
every pruned `QueryError` variant, and the register error-surface check
result.

**HARD STOP.**

---

## 5. Phase 1 — FOL Parse Boundary (`Either` at the Foundation Entry)

**Goal:** Implement the boundary conversion the corpus already prescribes
(ADR-007 C2: parser exceptions never escape the public API; ADR-012
§ Relationship) at the foundation parser's string-input entries. This is
the parse entry register's targeting predicates will use.

**TDD first.** `FOLParserSpec` extended; the other foundation suites gain a
small `parseOk`-style helper for mechanical adaptation:

- Valid input → `Right(formula)` (existing corpus adapted, results
  unchanged).
- Syntactically invalid input → `Left` carrying the failure message (and
  position/remaining-token information where the thrown exception provides
  it).
- Trailing unparsed input → `Left` (current `parseWithLexer` behavior,
  now typed).
- No exception escapes any string-input entry: malformed input on `parse`,
  `defaultParser`, and `parseWithLexer` is observed as `Left`, never as a
  thrown exception.

**Implementation:**

- New foundation-local parse-error type in the `parser` package (name and
  file placement per foundation conventions, e.g. `parser.ParseFailure`;
  ADR-006 encoding; carries message plus optional position/remaining
  payload). No `fol.*` import — the foundation stays a standalone usable
  core (ADR-004).
- `parser/FOLParser.scala`: `parse`, `defaultParser`, `parseWithLexer`
  return `Either[<foundation error>, Formula[FOL]]` via a single
  `try/catch` at the entry — the same boundary pattern
  `VagueQueryParser.parse` uses. `parseTokens` keeps the combinator tuple
  shape (C1); every internal combinator and its exception backtracking is
  untouched (C2 core).
- Call sites updated mechanically: `examples/FOLDemo.scala` (6 sites) and
  the foundation test suites (`FOLParserSpec`, `FOLUtilSpec`,
  `FOLPrinterSpec`, `FOLSemanticsSpec`).
- `docs/ADR-007.md`: deviation note recorded per its Review Criterion — the
  string-input public entries now perform the C2 boundary conversion;
  characteristics C1–C13 of the combinator core are unaffected.
- `docs/ADR-012.md` § Relationship to ADR-007: wording finalized — the
  statement about `FOLParser.parse` converting at the boundary is now true.

**Pass criterion:** `sbt test` green on both platforms; the
no-escaping-exception tests green; ADR-007 and ADR-012 notes written.

> This phase makes the `Either` form the only public form of `FOLParser`,
> which is what lets the §10.1 external-API audit assert zero calls to a
> throwing `FOLParser` form.

**HARD STOP.**

---

## 6. Phase 2 — ADR-017 Draft + Consistency Gate

**Goal:** The semantic decisions are written down as ADR-017 and checked for
consistency with the corpus before the code changes.

**ADR-017 must fix:**
1. Range semantics: `D_R = { d ∈ domain(sort(x)) | evalFormula(range,
   env + (x→d)) }`; negation is complement within the enumerated active
   domain (closed-world); soundness depends on range extraction being exact
   full enumeration (never sampled) — cross-reference ADR-003.
2. Validation rules for formula ranges: the quantified variable must occur
   **free** in the range formula; free range variables other than the
   quantified variable must be answer variables (`y' ⊆ y` restated over
   free variables via `FOLUtil.fvFOL`).
3. The typed IL change: `BoundQuery.range: BoundFormula`; single-atom ranges
   bind to `BoundFormula.Atom` (backward-identical evaluation).
4. Satisfying-set contract (Job 2): exact, deterministic, type-checked,
   `Either`-returning; free variables must equal exactly the given variable;
   the variable's sort must be a domain type (ADR-014); input is a
   pre-parsed `Formula[FOL]`, parsed via the Phase 1 `FOLParser` entry.
5. Consequence notes: range extraction may invoke dispatcher predicates and
   functions for every atom in the range formula; the T-002 `ConstRef`
   interim applies inside ranges.

**Consistency checklist (pass/fail, recorded in
`docs/scratch/ADR-017-consistency-review-<date>.md`):**
1. No statement contradicts ADR-001 §§1–2, ADR-002, ADR-007 C1–C13,
   ADR-012, ADR-014, ADR-015 §§1–5, or the post-Phase-0 corpus state.
2. Every surviving acceptance criterion (AC-1 … AC-8, AC-10) maps to a
   phase and a test in this plan.
3. The ADR-001 §3 edit staged for Phase 6 is drafted and consistent.
4. The `VagueQuantifiers.md` change list for Phase 6 is drafted (syntax
   table row "Range — FOL atom" → formula; closed-world note;
   satisfying-set section; variable scoping rules section).

**Pass criterion:** all items pass; any fail blocks Phase 3 until ADR-017 is
amended.

**HARD STOP.**

---

## 7. Phase 3 — Typed IL Widening (no syntax change)

**Goal:** Widen the IR and evaluator first, keeping the parser and
`ParsedQuery` untouched. After this phase the engine can evaluate compound
ranges built programmatically, and every parsed query behaves
byte-identically (single atom → `BoundFormula.Atom`).

**TDD first.** Extend the typed evaluation specs (programmatic `BoundQuery`
construction, no parser involvement):

- Compound-range semantics over a KB with unary predicates `p`, `q` on sort
  `S`, active domain `D` (AC-1 at IR level):
  - range `And(p(x), q(x))` → `rangeElements == P ∩ Q`;
  - range `Not(p(x))` → `D \ P`;
  - range `Or(p(x), q(x))` → `P ∪ Q`;
  - range `Exists(a: T, r(x, a))` → `{ d | ∃a. r(d,a) }`;
  - De Morgan property: `Not(And(p, q))` and `Or(Not(p), Not(q))` produce
    equal range sets.
- Denominator semantics (AC-2): `result.domainSize` and
  `output.rangeElements` equal the compound satisfying set, not the sort
  domain.
- Regression: a range of `BoundFormula.Atom(a)` evaluates identically to the
  previous `BoundAtom` range (assert on an existing scenario's full
  `EvaluationOutput`).

**Implementation:**

- `fol/typed/BoundQuery.scala`: `range: BoundAtom` → `range: BoundFormula`.
  Scaladoc updated to current state.
- `fol/typed/QueryBinder.scala`: `bind` wraps the existing
  `bindAtom(query.range, …)` result in `BoundFormula.Atom` (parser surface
  unchanged this phase).
- `fol/typed/TypedSemantics.scala`: `collectRangeElements` calls
  `evalFormula` instead of `evalAtom`.

**Pass criterion:** `sbt test` green on both platforms; the regression
assertion above green.

**HARD STOP.**

---

## 8. Phase 4 — Surface Widening (parser + `ParsedQuery` + binder)

**Goal:** The query language accepts formula ranges end-to-end.

**TDD first.**

- `VagueQueryParserSpec`:
  - Every existing test query still parses, and single-atom ranges produce a
    `ParsedQuery` whose range is the same atom (AC-3 at parse level).
  - New: ranges `~p(x)`, `p(x) /\ q(x)`, `p(x) \/ q(x)`,
    `exists a . r(x, a)`, a parenthesized compound, and an infix-comparison
    atom range; each followed by a scope — pins that the formula parser
    stops at the range/scope comma.
  - Validation: range not containing the quantified variable free (e.g.
    range `exists x . p(x)` for quantified `x`, or range `p(y)`) is a
    `ValidationError`; free range variable not in answer variables is a
    `ValidationError`.
- `QueryBinderSpec`: a range mixing incompatible sorts for the quantified
  variable fails at bind time with `ConflictingTypes` (AC-4); an inner
  range quantifier over a value type fails with `TypeNotQuantifiable`
  (ADR-014 carried into ranges).
- Typed end-to-end spec: parsed compound-range queries reproduce the
  Phase 3 IR-level results (AC-1 via the parser).
- Sampling pin (AC-6): with sampling params forcing a strict sample on the
  scope, `rangeElements` still equals the full compound satisfying set.

**Implementation:**

- `fol/logic/ParsedQuery.scala`:
  - `range: FOL` → `range: Formula[FOL]`.
  - Source compatibility for the single-atom case: `mk` overload (and the
    documented construction path) accepting `FOL`, wrapping in
    `Formula.Atom`. Direct case-class construction with a bare `FOL` becomes
    a compile error — intended, visible, and noted in the changelog (AC-10).
  - `rangeVars` := free variables of the range formula
    (`FOLUtil.fvFOL(range).toSet`); `mk` validation restated over it
    (quantified variable free in range; free-minus-x ⊆ answer variables).
- `fol/parser/VagueQueryParser.scala`: range production delegates to
  `FormulaParser.parse(FOLAtomParser.parseInfixAtom, FOLAtomParser.parseAtom)`
  — the same call the scope uses. No ADR-007 file changes.
- `fol/typed/QueryBinder.scala`: range binding becomes
  `bindFormula(query.range, Map.empty, catalog)`; the existing
  quantified-variable checks (`UnconstrainedVar`, `TypeNotQuantifiable`)
  operate on the resulting environment unchanged.

**Pass criterion:** `sbt test` green on both platforms; existing tests
untouched or mechanically adapted only (list every adapted test in the phase
report).

**HARD STOP.**

---

## 9. Phase 5 — Satisfying-Set Entry Point

**Goal:** Job 2's contract: evaluate a bare formula to its exact satisfying
set over one sort's domain.

**TDD first.** New spec (name per repo convention, e.g.
`SatisfyingSetSpec`):

- `¬`, `∧`, `∃` cases over a small KB; an empty-result case (AC-5).
- A formula with an extra free variable is rejected with the new typed
  error; a formula not containing the variable at all is rejected.
- A variable whose inferred sort is a `ValueType` is rejected with
  `TypeNotQuantifiable` (ADR-014).
- Determinism: two evaluations produce equal sets; exactness: result equals
  brute-force filter of the domain (no sampling parameters exist on this
  API).

**Implementation:**

- `fol/typed/QueryBinder.scala`: public standalone-formula binding — binds a
  `Formula[FOL]` from an empty environment, then validates: environment
  after binding contains exactly the given variable (new `TypeCheckError`
  variant for unexpected free variables — encoded per the existing
  `TypeCheckError` style, ADR-006), and the variable's sort is in
  `catalog.domainTypes` (`TypeNotQuantifiable`).
- `fol/typed/TypedSemantics.scala`: public
  `satisfyingSet(formula: BoundFormula, variable: BoundVar, model:
  RuntimeModel): Either[QueryError, Set[Value]]` — enumerates
  `model.domains(variable.sort)` and filters with the existing private
  `evalFormula` (no second evaluator, no sampling).
- `fol/semantics/VagueSemantics.scala`: facade
  `satisfyingSet(…, folModel: FolModel): Either[QueryError, Set[Value]]`
  composing bind → evaluate; `renderTypeErrors` gains the new arm. Input
  is a pre-parsed `Formula[FOL]` (text is parsed via the Phase 1
  `FOLParser` entry; consumers needing `QueryError` map the foundation
  parse error at their own boundary).

**Pass criterion:** `sbt test` green on both platforms; AC-5 checklist
covered by the new spec.

**HARD STOP.**

---

## 10. Phase 6 — Docs, ADR Acceptance, Version (AC-8, AC-10)

**Goal:** The documentation corpus describes the current state; ADR-017 is
binding; the version reflects the API change.

- `docs/VagueQuantifiers.md`: syntax table row Range → "FOL formula";
  closed-world-negation-over-active-domain section; satisfying-set section;
  variable scoping rules updated to the free-variable formulation; one
  compound-range example. (The untyped-path redirects already landed in
  Phase 0.)
- `docs/ADR-001.md` §3: `BoundQuery` snippet shows `range: BoundFormula`;
  prose "sort-checked range predicate" → "sort-checked range formula";
  cross-reference to ADR-017 for range semantics.
- ADR-017: status → Accepted after a code-vs-ADR consistency walk (each
  Implementation row ✅ / ⚠️ amend / ❌ fix, recorded in `docs/scratch/`).
- `docs/TODOS.md`: T-006 marked DONE (retirement landed in Phase 0).
- `README.md` + changelog note: untyped-backend removal, formula ranges,
  `satisfyingSet`, the breaking construction-site change to `ParsedQuery`,
  and any pruned `QueryError` variants (AC-10).
- `build.sbt`: version `0.10.2` → `0.11.0` (early-semver, pre-1.0: breaking
  API changes bump the minor). Publishing follows `docs/RELEASE.md` and is
  user-triggered; not part of this plan.
- Doc-consistency sweep over files touched by the change, including:
  `ParsedQuery.scopeVars` scaladoc (first line names `fvFOL` while the code
  uses `varFOL` — corrected to describe current behavior) and
  `VagueQueryParser` header examples.
- Comment-style cleanup in files Phase 3 brought into scope (deferred boyscout,
  Ruling of 2026-08-10, from the Phase 3 complex review): de-historicize the
  plan-phase / was-now comments while preserving their factual content — the
  `BoundQuery.scala` `LiteralRef` scaladoc ("After ADR-015 §4 / ADR-016 / PLAN
  Phase 5a …", "deferred (T-005)"), `TypedSemantics.scala` `ConstRef` comment
  ("see TODOS T-002"), and `QueryBinderSpec.scala` ("was a `LiteralValue`
  stopgap … PLAN Phase 5a" plus the "Phase 3/5a:" test names).
- Refresh the retained stale ADR illustrations (Ruling of 2026-08-10 — Phase 0
  residual ADR illustrations): the Decision/Code-Smells example blocks in
  ADR-003 (`resolved.evaluate()`), ADR-004 (`holdsVague`/`KnowledgeBase`,
  `RelationValue`/`DomainElement`), and ADR-012
  (`RelationName`/`KnowledgeBase`/`RangeExtractor`/`toResolved`) — restated
  over surviving types while preserving each ADR's principle.

### 10.1 External-API `Either` audit

**Goal:** Prove — not assume — that after all phases the entire external-facing
API is `Either`-based and no vague-layer code relies on the exception-based
form. This is the cross-cutting verification the user requires; it runs here
because the full public surface (parse boundary, formula ranges,
`satisfyingSet`) exists only after Phase 5. Recorded in
`docs/scratch/either-api-audit-<date>.md`.

**Check 1 — external entry points return `Either`.** Enumerate the public
surface consumers reach and assert each returns `Either` (or the foundation
parse-error `Either` for `FOLParser`). The confirmed set, verified against
register's actual call sites:

| Entry point | Return | Consumer path |
|---|---|---|
| `parser.FOLParser.{parse, defaultParser, parseWithLexer}` | `Either[<foundation error>, Formula[FOL]]` (Phase 1) | targeting-predicate parsing |
| `fol.parser.VagueQueryParser.parse` | `Either[QueryError, ParsedQuery]` | `QueryRequest.resolve`, SPA `AnalyzeQueryState` |
| `fol.semantics.VagueSemantics.{bindTyped, evaluateTyped, satisfyingSet}` | `Either[QueryError, A]` | `QueryServiceLive` |
| `fol.typed.QueryBinder.bind` | `Either[List[TypeCheckError], BoundQuery]` | internal, via facade |
| `fol.typed.TypedSemantics.{evaluate, satisfyingSet}` | `Either[QueryError, A]` | internal, via facade |
| `fol.typed.TypeCatalog.apply` | `Either[List[TypeCatalogError], TypeCatalog]` | model construction |
| `fol.typed.FolModel.apply` | `Either[QueryError, FolModel]` | model construction |

**Check 2 — no vague-layer reliance on the exception form.** Grep the surviving
vague main sources (`core/src/main/scala/fol/**`) and assert:
- zero `catch` of `QueryException` — the two trampolines in `VagueSemantics`
  (`holds`, `evaluate[D]`) and those in `RangeExtractor` / `ResolvedQuery` are
  gone with Phase 0; no new one is introduced;
- zero call to the throwing `FOLParser` form (there are none today; Phase 1
  makes the `Either` form the only public form, so this stays zero);
- every remaining `throw` / `QueryException(` lies in the allowlist below.

**Allowlist (sanctioned throwing — ADR-012 construction-invariant /
programming-error channel; each has an `Either` sibling):**

| Site | Why sanctioned | `Either` sibling |
|---|---|---|
| `TypeCatalog.unsafe` | Documented tests/startup-only; a catalog inconsistency is a programming error, not user input | `TypeCatalog.apply` |
| `require(...)` construction guards | ADR-012 construction-invariant channel | — (invariant, not state) |
| `VagueQueryParser` internal combinators + `ParsedQuery.mk` throw, caught once at `VagueQueryParser.parse` | ADR-002 combinator style, grandfathered (D-1 ruling, §2): composes with the ADR-007-frozen exception-backtracking foundation, so `Either`-internal would re-wrap exceptions at every foundation call; register never calls `mk` directly | `VagueQueryParser.parse` |

**Pass criterion (audit):** Check 1 table fully `Either`; Check 2 greps return
only allowlisted sites; audit file records the exact grep output.

**Pass criterion:** `sbt test` green on both platforms; every listed doc
updated; consistency walk recorded; §10.1 audit passes.

**HARD STOP.**

---

## 11. Acceptance-Criteria Traceability

| AC | Where satisfied |
|---|---|
| AC-1 compound ranges | Phase 3 (IR level) + Phase 4 (parser level), incl. De Morgan property |
| AC-2 denominator | Phase 3 test on `domainSize`/`rangeElements` |
| AC-3 backward compatibility | Phase 3 regression assertion + Phase 4 parser corpus + full suite green every phase |
| AC-4 sort unification | Phase 4 `ConflictingTypes` bind-time test (existing `mergeEnvs` machinery) |
| AC-5 satisfying set | Phase 5 spec (parse boundary covered by Phase 1 tests) |
| AC-6 sampling interaction | Phase 4 pin test |
| AC-7 cross-build | `sbt test` root aggregate (JVM + Scala.js) every phase |
| AC-8 docs | Phase 6 (untyped-path redirects in Phase 0) |
| AC-9 untyped path | Superseded by user ruling 2026-08-10: untyped backend retired in Phase 0 (T-006) |
| AC-10 versioning | Phase 6 (`0.11.0`, changelog note incl. removal, `FOLParser` signature change, pruned error variants) |
| External-API `Either` audit (user-required, not an AC) | Phase 6 §10.1 (Either-only external surface; no vague-layer exception-form reliance; allowlist enforced) |

---

## 12. Halt Marker

> 🛑 **Per WORKING-INSTRUCTIONS § Mandatory Review Halt, the agent now
> stops.** This plan is design only. No rulings are pending. Awaiting
> explicit user signal to begin Phase 0.
