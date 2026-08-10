# ADR-017 consistency review — 2026-08-10

Phase 2 gate of `PLAN-range-formula-and-satisfying-set.md`. Design-only: no
code changed this phase, so the suite state is the Phase 1 result (green, both
platforms). `docs/ADR-017.md` is authored at status **Proposed**; it flips to
**Accepted** in Phase 6 (§10) after the code-vs-ADR walk.

The four checklist items below are the §6 pass criterion. All pass.

---

## Item 1 — no contradiction with the corpus

Checked ADR-017 against ADR-001 §§1–2, ADR-002, ADR-007 C1–C13, ADR-012,
ADR-014, ADR-015 §§1–5, and the post-Phase-0 corpus state.

| Corpus point | ADR-017 statement | Verdict |
|---|---|---|
| ADR-001 §1 `TypeCatalog` validated-at-construction | ADR-017 adds no catalog surface; binds against the existing catalog | Pass |
| ADR-001 §2 `bind` is the single compilation step | Range binding is a call to the existing `bindFormula`; no second compilation path | Pass |
| ADR-002 combinator style / no re-wrapping mid-flight | Range production reuses `FormulaParser.parse(parseInfixAtom, parseAtom)`; parse stays behind `VagueQueryParser.parse`'s one `Either` boundary | Pass |
| ADR-007 C1–C13 | No ADR-007 file modified; grammar termination sound (comma not infix); `parseTokens` tuple shape (C1) untouched | Pass |
| ADR-012 error channels | `satisfyingSet` facade returns `Either[QueryError, A]`; text feed is the `FOLParser.parse` `Either` boundary; no new throwing surface | Pass |
| ADR-014 two orthogonal checks | `satisfyingSet` replicates the bind-time `TypeNotQuantifiable` check for its variable because it enumerates outside `bind`; the startup check is unaffected | Pass |
| ADR-015 §§1–5 literal/constant boundaries | Range atoms flow through the existing `LiteralRef`/`ConstRef`/`FnApp` branches; T-002 interim applies inside ranges | Pass |
| ADR-003 sampling | ADR-017 §2 states range extraction is never sampled — consistent with `collectRangeElements` (exhaustive) vs `evaluateOverRange` (sampled) | Pass |

ADR-001 **§3** (`range: BoundAtom`, "sort-checked range predicate") is the one
statement ADR-017 supersedes. This is the ruled deviation (§2 of the plan): §3
is edited for consistency in Phase 6 with a cross-reference. Checklist item 1
scopes to §§1–2 deliberately; the §3 edit is item 3. Not a fail.

---

## Item 2 — every surviving AC maps to a phase and a test

Matches the plan's §11 traceability table; restated here for the gate.

| AC | Phase | Test locus |
|---|---|---|
| AC-1 compound ranges | 3 (IR) + 4 (parser), incl. De Morgan property | typed eval specs + `VagueQueryParserSpec` |
| AC-2 denominator | 3 | typed eval spec on `domainSize`/`rangeElements` |
| AC-3 backward compat | 3 regression + 4 parser corpus + suite green each phase | full suite |
| AC-4 sort unification | 4 | `QueryBinderSpec` `ConflictingTypes` |
| AC-5 satisfying set | 5 (parse covered by Phase 1) | `SatisfyingSetSpec` |
| AC-6 sampling interaction | 4 | pin test on `rangeElements` under strict sampling |
| AC-7 cross-build | every phase | `sbt test` root aggregate |
| AC-8 docs | 6 (redirects in 0) | doc sweep |
| AC-9 untyped path | superseded (Phase 0, T-006) | n/a |
| AC-10 versioning | 6 | `0.11.0` + changelog |

Pass — no surviving AC is unmapped.

---

## Item 3 — ADR-001 §3 edit (staged for Phase 6, drafted here)

The §3 `BoundQuery` snippet and its prose. Applied in Phase 6, not now.

**Snippet — replace the `range` field line:**

```
-  range: BoundAtom,       // sort-checked range predicate
+  range: BoundFormula,    // sort-checked range formula (semantics: ADR-017)
```

**Prose — in the §3 lead-in, the phrase describing the range field:**

- "sort-checked range predicate" → "sort-checked range formula"
- Add one sentence after the snippet: "The range is a full FOL formula; its
  extraction semantics (compound population, closed-world negation over the
  active domain) are specified in ADR-017. A single-atom range binds to
  `BoundFormula.Atom` and evaluates identically to the prior atom-only path."

The §3 opening sentence ("`BoundQuery` is the canonical intermediate language
for the typed evaluation path") and §§1–2, §4, §5 are unchanged. Drafted edit
is consistent with ADR-017 §5 and introduces no claim ADR-017 does not carry.

---

## Item 4 — `VagueQuantifiers.md` change list (Phase 6, drafted here)

Line references are as of 2026-08-10 and may drift.

1. **Range description (~line 35).** `R(x, y')`: **Range predicate** — defines
   domain `D_R` → **Range formula** — defines domain `D_R`. Note it may be any
   FOL formula, single-atom being the common case.
2. **Extract-range step (~line 43).** `D_R = {d | KB ⊨ R(d, c)}` → restate as
   `D_R = { d ∈ domain(sort(x)) | evalFormula(range, env + (x→d)) }`; add the
   closed-world note: negation is complement over the active domain,
   conjunction is intersection, disjunction is union; extraction is exhaustive,
   never sampled (the soundness condition for negation).
3. **Query Syntax table (~line 72).** Row `| Range | FOL atom | country(x),
   risk_in_project(x, "Alpha") |` → `| Range | FOL formula | country(x),
   ~patched(x) /\ facing(x) |`.
4. **Paper-to-code map (~line 120).** Row `| R(x, y') | range: FOL (in
   ParsedQuery) | fol.logic |` → `range: Formula[FOL]`. The `D_R` /
   `collectRangeElements` rows (~123, ~134) stay accurate.
5. **Variable scoping rules (~lines 186–194).** Restate over free variables
   (`fvFOL`): (a) the quantified variable must occur **free** in the range;
   (b) other free range variables must be answer variables. Replace the
   atom-only examples with formula examples, including one with an inner range
   quantifier (e.g. `exists a . r(x, a)`) to show that `a` is bound and does
   not need to be an answer variable.
6. **New sections.** (a) Closed-world-negation-over-active-domain section
   (mirrors ADR-017 §§1–2). (b) Satisfying-set section: bare formula, one free
   variable, exact set over the sort's domain, no sampling, pre-parsed
   `Formula[FOL]` input (mirrors ADR-017 §6). (c) One worked compound-range
   example end to end.

Pass — the list is concrete and consistent with ADR-017.

---

## Gate result

All four items pass. Phase 3 is unblocked. Phase 2 changed no code; the suite
remains green from Phase 1 (JVM + Scala.js). HARD STOP — awaiting user
"proceed" for Phase 3.
