# TODOs

## T-000 — Scala-package rename `fol.*` → `vql.*`

**Status:** Implemented in the working tree — all nine `fol.*` packages renamed
to `vql.*` (core main + test), live docs swept, `build.sbt`/README/Architecture
at `0.13.0`, CHANGELOG `0.13.0` entry added; full suite green both platforms.
Remaining gate: the user's commit + publish of the `0.13.0` release, and
register's lockstep import rewrite. Sequenced after the fragment-membership API
(0.12.x). See "Release sequencing — T-011 and T-000" below and
[T-011](#t-011--fragment-membership-api-register-facing-targeting-0120).

**Scope:** rename every `fol.*` package across `core/src/**` — the complete set,
not a subset:
  `fol.error` → `vql.error`,
  `fol.logic` → `vql.logic`,
  `fol.parser` → `vql.parser`,
  `fol.quantifier` → `vql.quantifier`,
  `fol.result` → `vql.result`,
  `fol.sampling` → `vql.sampling`,
  `fol.semantics` → `vql.semantics`,
  `fol.typed` → `vql.typed`,
  and `fol.fragment` → `vql.fragment` once T-011 lands.
- Update all `import` statements within VQL.
- Downstream: register's entire `import fol.*` surface across the foladapter
  module rewrites in lockstep (RiskTreeKnowledgeBase, QueryServiceLive,
  QueryRequest, AppError, the foladapter specs). register consumes this only as
  a Central binary, so it adopts the rename as a published-release pin bump.

**Why its own release:** the rename touches register's whole import surface. Kept
apart from the fragment-membership API (T-011) so register absorbs one bounded
breaking change per pin bump — a pure mechanical import rewrite here, with no new
API to integrate in the same diff.

**Why not a major version:** pre-1.0 early-semver; the public API is deliberately
not stabilised yet.

**Plan:** `docs/PLAN-package-rename-fol-to-vql.md` (drafted; preconditions §9
before execution).

**Reference:** `docs/DONE_PLAN-symmetric-value-boundaries.md` §1 (Out of
scope) and §8 Phase 6 (Step 6.1).

---

## ~~T-001 — Tagged type constructors for domain vs value type in the programmatic `TypeCatalog` API~~ ✅ Implemented in `0.7.0-SNAPSHOT`

**Status:** DONE. `DomainType(id)` / `ValueType(id)` ADT implemented in `fol/typed/TypeDefs.scala`.
`TypeCatalog.unsafe(types = Set(DomainType(asset), ValueType(loss)), ...)` — no `domainTypes` parameter.
`catalog.domainTypes` is a derived method. See ADR-014 §1.
---

## T-002 — Named constants: design review and correctness gap

**Status:** ✅ RESOLVED by ADR-015 §4 + Phase 3 of `DONE_PLAN-symmetric-value-boundaries.md` (2026-05-02). `BoundTerm.ConstRef.raw` is now `Any` and inline literals carry the parsed primitive (or consumer-chosen wrapper) produced by the registered `literalValidator`, not `TextLiteral` of the source text. A validator returning `None` produces the new `TypeCheckError.UnparseableConstant(name, sort, sourceText)`. Named constants registered via `catalog.constants` (option (a)/(c) of the original design space) are still treated as a separate path; the design space below is preserved for the historical record.

**Original status (2026-04-03):** DEFERRED.

The `catalog.constants: Map[String, TypeId]` feature is an OCaml-heritage artifact. A named constant bound through `QueryBinder` currently falls into the `catalog.constants.get(name)` branch in `bindTermExpected` and produces `ConstRef(name, expected, TextLiteral(name))` — the raw value is a `TextLiteral` of the source text, which is semantically incorrect for typed consumers expecting a `Long` or `Double` value at evaluation time.

**Why this is deferred and not fixed now:**
- No paper requirement: Fermüller et al. do not define named constants as a query language feature.
- No end-to-end tests cover named constants; only catalog-level schema validation tests exist.
- The correct design is non-trivial and intersects with T-003.

**Design space for a future decision (do not prescribe a solution prematurely):**
- (a) Remove named constants entirely as out-of-scope — `catalog.constants` is deleted; any `Term.Const(c)` that is not an inline literal is a bind error.
- (b) Route named constants through the same `LiteralValue`/validator mechanism as inline literals — the consumer registers a validator that recognises the constant name and returns the appropriate `LiteralValue`.
- (c) A separate named-constant registry where consumers supply the full `Value(sort, raw)` directly, bypassing the validator mechanism.

**Context:** ADR-015 §1 (injection boundary), `fol/typed/QueryBinder.scala` `bindTermExpected` `Term.Const` branch, conversation history 2026-04-03.

---

## T-003 — Typed literal pipeline and function return-type normalizer

**Status:** DEFERRED (documented as-is, 2026-04-03).

A `TypedFunctionImpl.of` combinator pattern was sketched where the consumer lambda returns a native `A` and a `wrap: A => LiteralValue` normalizer converts it, so the framework constructs `Value(resultSort, literalValue)`. This eliminates the two-tier raw world for value-type function results: currently a function lambda can return any `raw` (e.g. a plain `Double` 0.07), while inline literals produce `LiteralValue` variants (`FloatLiteral(0.05)`). Downstream dispatcher lambdas that receive arguments from both sources must handle both raw shapes.

**Why this is deferred and not fixed now:**
- Requires `FolModel` API to be stable first (planned next).
- Requires a design decision on whether the normalizer is part of `MapDispatcher` (registration-time wrapping) or a separate wrapper combinator.
- The `LiteralValue` foundation is now in place (ADR-015, T-002 unresolved), so this can proceed once T-002 and `FolModel` are settled.

**Context:** ADR-015 §1 and Code Smells §4, `MapDispatcherSpec` `rawToDouble` helper (shows the two raw shapes), conversation history 2026-04-03.
---

## T-004 — Domain-returning functions and entity identity representation

**Status:** DEFERRED pending use case (2026-04-04).

The function return normalisation plan (T-003 / `PLAN-function-return-normalisation.md`)
scopes `evalFunction` return type to `Either[String, LiteralValue]`. This implicitly
constrains all functions to return `ValueType` sorts — a function declared with a
`DomainType` return (e.g. `ownerOf: Asset → Company`) cannot be expressed as a
`LiteralValue` without re-introducing `Any` or adopting an explicit entity identity
type.

**The core tension:** a `LiteralValue` variant for entity references (`EntityRef(key)`)
would need a key type. Options examined:

- `EntityRef(key: String)` — defeats the purpose; `String` is too permissive and
  reintroduces the untyped-raw smell at a different level
- `EntityRef(key: Int)` — avoids string ambiguity but raises questions about whether
  integer keys are sufficient (security, composites, natural keys from external systems)
- `EntityRef(key: Any)` — directly re-introduces `Any`; rejected

**Why deferred:** no current use case requires a domain-returning function. The
`ownerOf: Asset → Company` pattern does not appear in any planned query model.
The right answer depends on whether integer keys suffice for all anticipated domains,
which requires a concrete use case to evaluate.

**Prerequisite for T-003 implementation:** T-003 must add a catalog-validation guard
(`TypeCatalogError.FunctionReturnIsDomainType`) that rejects domain-returning function
declarations at construction time, making the current scope limitation explicit and
surfacing it as an error rather than silent misbehaviour. This guard is removed when
T-004 is resolved.

**Context:** `PLAN-function-return-normalisation.md` §Q2, `fol/typed/TypeCatalog.scala`
`collectErrors`, conversation history 2026-04-04.

---

## T-005 — `Carrier[A]` GADT for `LiteralRef.value` (deferred from PLAN Phase 5c)

**Status:** Deferred (recorded 2026-05-02 at DONE_PLAN-symmetric-value-boundaries.md
Phase 5b HARD STOP). Re-evaluate when a second literal-walking consumer appears.

**Trigger to re-open:** introduction of any consumer that walks `LiteralRef`
nodes directly without going through `Extract[A]` — e.g. a serializer, a code
generator, a debugger/printer that needs static exhaustivity over carriers.

**Scope:** Replace `BoundTerm.LiteralRef(sourceText, sort, value: Any)` with
`LiteralRef[A](sourceText, sort, carrier: Carrier[A], value: A)`. Add
`sealed trait Carrier[A]` with library givens for `Long`, `Double`, `String`
and a user-extensible registration path. Optionally consolidate
`LiteralParser[A]` / `Extract[A]` / `Carrier[A]` into a single `LiteralType[A]`
super-typeclass.

**Authoritative design:** [ADR-016](ADR-016-carrier-witness-on-symmetric-value-typeclasses.md).

**Implementation sketch:** see `DONE_PLAN-symmetric-value-boundaries.md` §7.3.

---

## T-006 — Retire the untyped evaluation backend

**Status:** ✅ DONE (Phase 0 of
`docs/DONE_PLAN-range-formula-and-satisfying-set.md`, 2026-08-10). The untyped
backend and its demos/tests were deleted per that plan's §4.1 inventory;
ADR-005/008/009/010 are Deprecated. The typed many-sorted backend
(`VagueSemantics.evaluateTyped` → `fol.typed`) is the only evaluation path.
The evaluation context below is preserved for the record.

**Context:** The untyped evaluation backend — `fol.semantics.VagueSemantics.holds`/`evaluate`,
`fol.semantics.RangeExtractor` (`buildPattern` / `DomainExtraction`), and the
`fol.datastore.KnowledgeSource` / `DomainCodec` machinery — has **no production consumer**.
The register project, the only downstream user, evaluates exclusively via the typed
many-sorted backend (`VagueSemantics.evaluateTyped` → `fol.typed.TypedSemantics`,
`QueryBinder` → `BoundQuery`). The untyped path is live only in this engine's own demos
(`examples/VagueSemanticsDemo`, `fol/examples/CyberSecurityExamples`) and its tests. "Used
only by tests and demos" is a dead-code smell.

**Scope to evaluate:**
- (a) Retire the untyped backend and migrate the demos to the typed path; or
- (b) keep it as a documented reference implementation of the pre-typed semantics.
- Range expressiveness (`∧`/`¬`/`∃` in the range) that register may need lands in the
  **typed** path (`TypedSemantics.collectRangeElements`), not in `buildPattern` — so the
  untyped path is not the place that extension would live either.

**Reference:** register `docs/scratch/MITIGATION-PRE-PLANNING.md` §P-4, §P-5.

---

## T-007 — Fresh typed-path demo

**Status:** PENDING — write after Phase 5 of
`docs/DONE_PLAN-range-formula-and-satisfying-set.md`, against the finished API
(formula ranges + `satisfyingSet`).

**Context:** Phase 0 (T-006) deleted the untyped demos
(`examples/VagueSemanticsDemo`, `fol/examples/*`) with no port. The library
currently has no runnable demo of the typed pipeline; `README.md` points at
`VagueSemanticsTypedSpec` as the worked example. A small runnable demo
(`TypeCatalog` → `RuntimeModel` → `FolModel` → `evaluateTyped`, plus a
compound-range and a `satisfyingSet` example) should be added once the range
and satisfying-set features land, so the demo exercises the complete API.

**Why deferred:** Writing it now would demo an API that Phases 3–5 change.
Deferring to post-Phase-5 lets the demo cover formula ranges and
`satisfyingSet` in one pass.

---

## T-008 — Prune dead `QueryError` variants

**Status:** PENDING — standalone task, **not** part of the range-formula
workstream. Sequence as its own commit; coordinate with a register upgrade
(breaking change).

**Goal:** `fol.error.QueryError` declares 19 variants; **10 are raised by no
main-source code** — dead public surface, much of it generic scaffolding that
predates the current typed pipeline. Remove them so the type honestly reflects
what the engine returns.

**Variants to remove** (zero main raisers as of 2026-08-10):
`LexicalError, QueryStructureError, QuantifierError, ScopeEvaluationError,
UninterpretedSymbolError, TypeMismatchError, ResourceError, ConnectionError,
TimeoutError, ConfigError`.

**Before removing — verify (the earlier-work method):**
1. Re-confirm zero main raisers:
   `grep -rn "QueryError\.<Variant>(" core/src/main` (skip the `case class` def).
   A variant with a surviving raiser stays.
2. Check test-only raisers in this repo and delete/adjust those tests.
3. **Register is the gate.** Register's `AppError.scala` defensively maps **all
   10** variants (`case e: QE.<Variant> => …`), and
   `FolQueryFailureFromQueryErrorSpec.scala` constructs several. Pruning is a
   coordinated breaking change: the corresponding register `case` arms + test
   cases must be removed in the same register upgrade. Exact register
   lines/arms are catalogued in
   `docs/scratch/register-breaking-changes-2026-08-10.md` §5.

**Caveats / not in scope:**
- Breaking change to a published type → changelog note + minor version bump
  (early-semver pre-1.0). Do it in its own release, ideally bundled with the
  register upgrade that already adapts to the 0.11.0 error changes.
- The related smell — `BindError` / `ModelValidationError` carry `List[String]`
  rather than structured typed errors, due to the `fol.error → fol.typed`
  package constraint — is a **separate, larger** change (it means deciding that
  dependency direction). Do not bundle it here.
- Cross-layer / cross-phase error-hierarchy consolidation is a non-goal: it
  would break ADR-004 (foundation must not import vague; this is why
  `parser.ParseError` is foundation-local) and the `fol.error → fol.typed`
  boundary.

**Context:** analysis of 2026-08-10 (after PLAN-range Phase 1). See also
`docs/scratch/register-breaking-changes-2026-08-10.md`.

---

## T-009 — Evaluator short-circuit and dispatcher-error masking under non-total models (investigation)

**Status:** PENDING — investigation. Surfaced by the Phase 3 complex review
(2026-08-10, `DONE_PLAN-range-formula-and-satisfying-set.md`).

**Observation:** `TypedSemantics.evalFormula` short-circuits `And`/`Or`/`Imp`
and the `∀`/`∃` folds. When a dispatcher raises `Left(EvaluationError)` for some
atom or binding, whether that error surfaces depends on whether short-circuiting
reaches it:
- In `And(p, q)` the error on `q(d)` is masked when `p(d)` is false — driven by a
  model value, so deterministic per `(model, element)`: the same model always
  yields the same result. Not a cross-client discrepancy.
- In an inner quantifier `∃a . r(x, a)`, whether an erroring binding of `a`
  surfaces depends on the iteration order of `a`'s domain (`domain.toList`, i.e.
  `Set` order) — a non-semantic detail. Deterministic and cross-platform stable
  for content-`hashCode` carriers (`String`/`Long`/`Double`, all register uses);
  an identity-`hashCode` carrier would make it order-unstable (shared root cause
  with [T-010]).

**Why it is not a bug today:** every range/scope predicate register uses is
total (error-free) over the active domain, and its carriers are content-hashed.
The set-algebra reading of a range formula is exact precisely on total models.

**To investigate:** whether to add an "error-strict" evaluation mode that
surfaces any dispatcher error regardless of short-circuit (making a non-total
model a hard failure rather than a data- or order-dependent one), or to keep the
current short-circuit contract and document range/scope-predicate totality as a
precondition. ADR-017 §7 records the current behavior; this task decides whether
to change it.

**Context:** Phase 3 complex review findings 3 and 4;
`fol/typed/TypedSemantics.scala` `evalFormula`. See also T-010.

---

## T-010 — HDR sample subset determinism depends on `Set` iteration order (investigation, low priority)

**Status:** PENDING — investigation, LOW priority. Sampling is not exercised in
practice today (register evaluates with `SamplingParams.exact`), but the gap
should be known and fixed. Surfaced by the Phase 3 complex review (2026-08-10).

**Observation:** `HDRSampler.sample` builds `population.toArray` and runs a
partial Fisher-Yates shuffle with the deterministic HDR PRNG. The PRNG is
reproducible, but the array is materialised in `Set` iteration order, so the
*selected subset* is reproducible only when the element `hashCode` is
content-based. With a `Value.raw` carrier using identity `hashCode`, two runs on
identical inputs can select different subsets — contradicting `HDRSampler`'s
scaladoc ("reproducible … across all platforms") and ADR-003's cross-platform
reproducibility guarantee.

**Why low priority:** the only sampled path is scope estimation under non-exact
`SamplingParams`, which no current consumer uses; and the carriers in use
(`String`/`Long`/`Double`) are content-hashed, so the guarantee holds for them.
It bites only a future consumer that both samples and uses an identity-`hashCode`
carrier.

**To investigate / fix:** impose a stable total order on the population before
`toArray` (e.g. sort by a stable key derived from `Value`), or document and
enforce a hard precondition that sampled carriers have content-based `hashCode`.
Shared root cause with T-009.

**Context:** Phase 3 complex review finding 5; `fol/sampling/HDRSampler.scala`
`sample`/`toArray`; ADR-003 (reproducibility claim). See also T-009.

---

## T-011 — Fragment-membership API (register-facing, targeting 0.12.0)

**Status:** IMPLEMENTED (2026-08-12) — awaiting release. `fol.fragment`
(`Fragment`, `FragmentViolation`, `FragmentCheck`) is in main with unit and
parse-through tests; both fragments (`Targeting`, `Screening(k)`) ship.
[ADR-018](ADR-018.md) records the design (Accepted 2026-08-12);
[PLAN-fragment-membership-api.md](PLAN-fragment-membership-api.md) is the plan.
Bundles as the additive (non-breaking) Central release **0.12.0**, ahead of the
T-000 package rename (0.13.0). See "Release sequencing — T-011 and T-000" below.
The contract and rulings below are retained as the specification of the shipped
code.

**Why it exists:** register's write-path validation
(`PLAN-RISKTRANSFORM.md` §8.4-3, ruled 2026-08-10) needs to reject, at its HTTP
boundary, any formula outside a declared fragment before that formula reaches
typed bind. The check belongs on the engine's parse tree, not in register.

**Contract (from register §8.4-3):**
- A structural membership test over a parsed `Formula[FOL]` — the inert parse
  tree `FOLParser.parse` returns as `Either[parser.ParseError, Formula[FOL]]`.
  Runs on the parse tree only, before any typed bind. Does not reshape or fork
  the parser.
- One implementation, two fragment specs:
  - **targeting**: no quantifier nodes, no function-application terms;
  - **screening**: quantifier depth ≤ k.
- **Structural / sort-agnostic** — embeds no sort logic. register enforces its
  sort rule (P-1) separately at bind time. Pre-M3 the targeting fragment simply
  admits no quantifiers; at M3 the bind-time sort rule takes over and this
  machinery is unchanged.
- The return value carries **which fragment rule was violated**, not a bare
  boolean, so register can emit a specific 400.

**Why here, not a restricted parser entry point:** the Harrison-port parser core
is preserved verbatim (ADR-007, characteristics C1–C13); a restricted parser
would fork that protected core for no gain. A membership test on the parse tree
accepts exactly the same string set, and rejecting before typed bind keeps the
"reject at the language boundary" property intact.

**Proposed home + name** (engine's call; register left name and fragment-spec
shape open): `fol.fragment.FragmentCheck` — a new sibling package of `fol.typed`.
It walks a foundation `Formula[FOL]` and needs no `TypeCatalog`, so it stays
independent of the typed machinery it sits beside. Alternative considered:
place it directly in `fol.typed`.

**Proposed shape:**
```scala
package fol.fragment

enum Fragment:
  case Targeting                            // no quantifiers, no function-application terms
  case Screening(maxQuantifierDepth: Int)   // quantifier depth ≤ k

enum FragmentViolation:
  case QuantifierNotAllowed                          // targeting: a Forall/Exists node is present
  case FunctionApplicationNotAllowed(function: String) // targeting: a Term.Fn is present
  case QuantifierDepthExceeded(limit: Int, found: Int) // screening: depth > k

object FragmentCheck:
  /** Right(()) iff `formula` lies in `fragment`; Left carries the first
    * violated rule. */
  def check(formula: Formula[FOL], fragment: Fragment): Either[FragmentViolation, Unit]
```

**Feasibility of "which rule was violated": confirmed.** The parse tree carries
every structural fact the two specs need:
- quantifier nodes = `Formula.Forall` / `Formula.Exists`;
- function-application terms = `Term.Fn` (distinct from `Term.Var` and
  `Term.Const`; inline literals parse to `Term.Const`, so the targeting fragment
  admits variables and literals and rejects only `Fn`);
- quantifier depth = nesting depth of `Forall` / `Exists`.

`FragmentViolation` names the specific rule, which register maps to a specific
400. The `Either` return with a typed error mirrors `TypeCheckError` (the
house style).

**Return granularity — RULED (user, 2026-08-11):** first violation. `check`
returns `Either[FragmentViolation, Unit]`, `Left` carrying the first violated
rule. Membership is yes/no-with-reason; a single reason is enough for register's
400. A future `Either[List[FragmentViolation], Unit]` (all offending nodes) is a
strictly additive widening if register later wants it — not carried now.

**Fragment scope for 0.12.0 — RULED (user, 2026-08-12):** ship both fragments
(`Targeting` and `Screening(k)`) in 0.12.0, not targeting-only.

**Depth-counting convention — RULED (user, 2026-08-12):** maximum nesting depth
(quantifier rank), 0-indexed. `Screening(k)` admits a formula when the largest
number of quantifier nodes (`Forall` / `Exists`) on any single root-to-leaf path
is ≤ `k`; side-by-side quantifiers do not add up, only nesting does. So
`Screening(0)` admits no quantifiers (identical to targeting's quantifier rule),
`Screening(1)` admits one level of nesting, and `∃` inside `∃` needs
`Screening(2)`. This is the cost-relevant quantity (each nesting level multiplies
domain enumeration) and matches the standard meaning of the API word "depth". A
total-quantifier-count budget, if register ever needs one, is a separate named
fragment added additively — not this one.

**Fixed parse-tree facts register absorbs (not choices):**
- **`Term.Const` vs `Term.Fn`.** `FOLParser.parse` emits inline literals
  (`42`, `"IT Risk"`, const-named bare identifiers) as `Term.Const`, and
  everything applicative — `f(args)`, nullary `f()`, unary minus `-x`, and the
  arithmetic operators `+ - * / ^ ::` — as `Term.Fn`. "No function-application
  terms" therefore means "no `Term.Fn` node anywhere in the formula's terms":
  the targeting fragment admits variables and literals and rejects every `Fn`,
  including arithmetic operators. This is the engine's fixed representation;
  register consumes it, it is not a knob register sets.
- **Bare formula, not `ParsedQuery`.** The check is over a `Formula[FOL]` from
  `FOLParser.parse` — register's write-path formula validation — not the
  vague-query (`VagueQueryParser` → `ParsedQuery`) path. Consistent with §8.4-3.

**Implemented under [PLAN-fragment-membership-api.md](PLAN-fragment-membership-api.md)
and [ADR-018](ADR-018.md).** The code in `fol.fragment` follows the shape above:
first-violation `Either[FragmentViolation, Unit]`, pre-order left-to-right
traversal, `Term.Const`/`Term.Var` admitted and every `Term.Fn` rejected by
`Targeting`, and 0-indexed quantifier-rank depth for `Screening(k)`. Ships in
0.12.0 (ADR-018 accepted 2026-08-12); the release push is the remaining gate.

---

## Release sequencing — T-011 and T-000

register consumes this engine only as Central binaries and absorbs one bounded
breaking change per pin bump. Sequenced so each release is independently
adoptable:

| Version | Change | Breaking? | Rationale |
|---|---|---|---|
| 0.11.0 (shipped) | formula ranges + `satisfyingSet`; 4 `QueryError` variants removed | breaking (error surface) | on Central; register migrates AppError + prunes one server test |
| **0.12.0** | fragment-membership API ([T-011](#t-011--fragment-membership-api-register-facing-targeting-0120)) | **no — additive** | register needs it for M2/M3 write-path validation; adds no import rewrite, adopted on register's schedule |
| **0.13.0** | package rename `fol.*` → `vql.*` ([T-000](#t-000--scala-package-rename-fol--vql)) | **yes — import rewrite** | isolated so register's pin bump is a pure mechanical import rewrite, including `fol.fragment` → `vql.fragment`, with nothing else to reason about |

**Order rationale:** fragment API first because register needs it sooner and it
is non-breaking, so register adopts without a forced import rewrite. The rename
lands last so register does the whole-surface import rewrite once, against the
final package layout (fragment API included).

**T-008** (prune 10 more dead `QueryError` variants) is a **third, separate**
breaking release — never bundled with T-000 or T-011 — coordinated with the
register AppError change it forces. Version TBD, after 0.13.0.

**Open for register to confirm:** the order between 0.12.0 and 0.13.0 depends on
register's M2/M3 timing versus its readiness to do the import rewrite. Engine
recommends fragment-first as above; register may request the reverse given
advance notice of the target versions.
