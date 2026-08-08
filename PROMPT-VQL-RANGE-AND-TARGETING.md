# Agent prompt — vql-engine: range expressiveness (B) + targeting satisfying-set API

You are implementing two changes in the **vague-quantifier-logic** repository (sibling checkout:
`../vague-quantifier-logic` relative to the register repo). Work only in that repo. Follow its own
governance: read `docs/WORKING-INSTRUCTIONS.md`, the ADRs under `docs/` (ADR-001 … ADR-016), and
`docs/TODOS.md` before coding. The downstream consumer is the **register** project; the acceptance
criteria below are the interface contract register's build plan is designed against — do not
deviate from them without flagging it.

## Background (full context)

The engine implements first-order logic with vague quantifiers (Fermüller et al. 2016). A query is
`Q[op]^{k/n} x (R(x), φ(x))(y)`:

- **Range `R`** defines the population `D_R` (the proportion's denominator). Today it is a **single
  positive FOL atom**: `ParsedQuery.range: FOL` (`core/src/main/scala/fol/logic/ParsedQuery.scala`),
  `BoundQuery.range: BoundAtom` (`core/src/main/scala/fol/typed/BoundQuery.scala`).
- **Scope `φ`** is a full formula and already runs complete FOL semantics — `¬`, `∧`, `∨`, `⟹`,
  `⟺`, `∃`, `∀` — via `TypedSemantics.evalFormula`
  (`core/src/main/scala/fol/typed/TypedSemantics.scala`).

There are two evaluation backends. The **typed** many-sorted path
(`fol.typed`: `QueryBinder` → `BoundQuery` → `TypedSemantics`) is the only one register uses
(`VagueSemantics.evaluateTyped`). The **untyped** path (`fol.semantics.RangeExtractor` /
`VagueSemantics.holds`) is used only by this repo's demos/tests; it is **out of scope** here — do
not extend it (see `docs/TODOS.md` T-006 for its status).

Range extraction in the typed path is `TypedSemantics.collectRangeElements`: it **enumerates the
quantified variable's sort domain and tests the range atom per element** (`evalAtom`). There is no
relation index — evaluation is enumerate-and-test over a **finite active domain**. This is why the
extension below is cheap: negation over a finite enumerated domain is closed-world complement by
construction; conjunction is intersection.

Register's motivating use cases:
1. **Analytics queries over sub-populations** — e.g. "of the *mitigated* leaves, do ≥3/4 still
   exceed p99 loss 1M?" needs range = a compound/negated population (`mitigated(x)`,
   `¬mitigated(x)`, `leaf(x) ∧ mitigated(x)`, `∃a. mitigate(x,a)`), which a single positive atom
   cannot express.
2. **Mitigation targeting** — register stores per-mitigation *targeting predicates*: pure FOL
   formulas (no vague quantifier, no answer variables) whose **satisfying set** over the node
   domain is the mitigation's scope. Register needs a first-class "evaluate formula → satisfying
   set" entry point instead of wrapping a dummy quantifier query.

## Job 1 — Range accepts a full formula (typed path)

Widen the range from a single positive atom to a formula:

- `ParsedQuery.range: FOL` → `Formula[FOL]` (keep a source-compatible way to treat the
  single-atom case; register touches `ParsedQuery` at its HTTP boundary).
- Parser (`fol.parser.VagueQueryParser`): the range production accepts a formula. Preserve existing
  syntax — every currently valid query parses identically.
- `QueryBinder` / `BoundQuery`: `range: BoundAtom` → `BoundFormula`, reusing the existing scope
  formula binding. **Sort inference for the quantified variable must unify** across all range atoms
  where it appears; inconsistent sorts are a `TypeCheckError` (do not silently pick one).
- `TypedSemantics.collectRangeElements`: evaluate the range with `evalFormula` instead of
  `evalAtom`. Semantics: `D_R = { d ∈ domain(sort(x)) | evalFormula(range, env + (x→d)) }`.
  Negation is thereby **closed-world over the active domain** — document this explicitly in
  `docs/VagueQuantifiers.md`.
- Validation rules that reference the range (quantified variable must appear in the range;
  `y' ⊆ y` for answer variables) must be re-specified for formula ranges: the quantified variable
  must occur **free** in the range formula; free range variables other than `x` must still be
  answer variables.

## Job 2 — Satisfying-set entry point (targeting)

A public API that evaluates a bare formula (no quantifier, no answer variables) to its satisfying
set over one sort's domain. Suggested shape (adapt to repo conventions; keep the contract):

```scala
// in fol.semantics.VagueSemantics (or a better home per repo conventions)
def satisfyingSet(
  formula: Formula[FOL],        // parsed via the existing FOLParser (formula-level, not VagueQueryParser)
  variable: String,             // the single free variable
  folModel: FolModel            // catalog + runtime model, as in evaluateTyped
): Either[QueryError, Set[Value]]
```

Contract:
- Type-checks the formula via the existing binder machinery (sort of `variable` inferred/unified
  as in Job 1; a formula whose free variables ≠ {`variable`} is a validation error).
- Evaluates **exactly** (full domain enumeration, no sampling), deterministically.
- Reuses `evalFormula` — no second evaluator.

## Acceptance criteria (register designs against these — treat as the contract)

1. **AC-1 Compound ranges evaluate correctly.** For a KB with unary predicates `p`, `q` over sort
   `S` with active domain `D`: range `p(x) ∧ q(x)` yields `P ∩ Q`; range `¬p(x)` yields `D \ P`;
   range `p(x) ∨ q(x)` yields `P ∪ Q`; range `∃a:T. r(x,a)` yields the projection
   `{d | ∃a. r(d,a)}`. Verified by unit tests including a De Morgan property
   (`¬(p ∧ q)` ≡ `¬p ∨ ¬q` as range sets).
2. **AC-2 Denominator semantics.** For any formula range `R`, the reported
   `rangeElements`/`domainSize` equal the satisfying set of `R` — the proportion's denominator is
   the compound population, not the whole sort domain.
3. **AC-3 Backward compatibility.** Every query valid before this change parses and evaluates to
   byte-identical results (single-atom range unchanged). The full existing test suite stays green.
4. **AC-4 Typing.** Quantified-variable sort unification across range atoms; a range mixing
   incompatible sorts for `x` fails at bind time with a typed error (not at evaluation).
5. **AC-5 Satisfying-set API.** `satisfyingSet` exists per the Job-2 contract: exact, deterministic,
   type-checked, `Either`-returning; a formula with extra free variables is rejected; tested
   including `¬`/`∧`/`∃` cases and an empty-result case.
6. **AC-6 Sampling interaction.** Range extraction remains always-exact (full enumeration);
   sampling continues to apply only to scope evaluation over the extracted range. A test pins this.
7. **AC-7 Cross-build.** The library still compiles for both JVM and Scala.js (register's SPA runs
   `VagueQueryParser.parse` in-browser) — both targets' tests green.
8. **AC-8 Docs.** `docs/VagueQuantifiers.md` updated: formula ranges, closed-world negation over
   the active domain, the satisfying-set API. `docs/TODOS.md` untouched except, if relevant, a
   cross-reference from T-006.
9. **AC-9 Untyped path untouched.** No changes to `fol.semantics.RangeExtractor` /
   `DomainExtraction` / `KnowledgeSource` beyond what compilation strictly requires (goal: zero).
10. **AC-10 Versioning.** Version bumped per this repo's conventions (pre-1.0 SNAPSHOT; see
    `docs/TODOS.md` T-000 note) with a changelog/README note of the API change
    (`ParsedQuery.range` widening is breaking for direct constructors).

## Out of scope

- Retiring the untyped backend (T-006) — separate decision.
- The `fol.*` → `vql.*` package rename (T-000).
- Any register-side changes (register wires these APIs under its own plan).
- Vague/sampled semantics changes beyond AC-6's pin.

## Verification

`sbt test` (all suites, both platforms if the build is cross-configured) green before reporting
done. Report pass/fail only. List every doc updated.
