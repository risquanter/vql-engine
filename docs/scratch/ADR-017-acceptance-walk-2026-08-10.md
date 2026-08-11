# ADR-017 acceptance walk — 2026-08-10

Code-vs-ADR consistency walk run before flipping ADR-017 status
Proposed → Accepted (`PLAN-range-formula-and-satisfying-set.md` Phase 6).
Each Implementation-table row checked against the actual source.

| ADR-017 Implementation row | Verdict | Evidence |
|---|---|---|
| `BoundQuery.scala` — `range: BoundFormula` | ✅ | `case class BoundQuery(... range: BoundFormula, scope: BoundFormula, ...)` |
| `QueryBinder.scala` — range bound via `bindFormula`; standalone `bindSatisfyingFormula` with free-vars-equal-`{v}` check | ✅ | `bind` for-comprehension binds `query.range` via `bindFormula(_, Map.empty, catalog)`; `bindSatisfyingFormula(formula, variable, catalog)` binds from empty env, validates presence (`UnconstrainedVar`) → extras (`UnexpectedFreeVar`) → domain-type (`TypeNotQuantifiable`) |
| `TypedSemantics.scala` — `collectRangeElements` via `evalFormula`; `satisfyingSet` enumerates `domains(sort(v))` filtered by `evalFormula` | ✅ | `collectRangeElements` folds `rootDomain` with `evalFormula(query.range, ...)`; `satisfyingSet(formula, variable, model)` folds `domains(variable.sort)` with `evalFormula`, `DomainNotFoundError` on missing domain, no sampling |
| `TypeCheckError.scala` — new variant for an unexpected free variable | ✅ | `case UnexpectedFreeVar(name: String)` |
| `ParsedQuery.scala` — `range: Formula[FOL]`; validation restated over `fvFOL(range)` | ✅ | `range: Formula[FOL]`; `rangeVars = FOLUtil.fvFOL(range).toSet`; `mk` validates `x ∈ rangeVars` and `rangeVars - x ⊆ answerVars` |
| `VagueQueryParser.scala` — range production delegates to `FormulaParser.parse` | ✅ | step 4 uses `FormulaParser.parse(FOLAtomParser.parseInfixAtom, FOLAtomParser.parseAtom)(t3)` |
| `VagueSemantics.scala` — `satisfyingSet` facade; `renderTypeErrors` arm for the new variant | ✅ | `satisfyingSet(formula, variable, folModel)` composes `bindSatisfyingFormula` → `TypedSemantics.satisfyingSet`, binder errors → `BindError(renderTypeErrors(...))`; `renderTypeErrors` has the `UnexpectedFreeVar` arm (and the `UnparseableConstant` arm that closed the pre-existing non-exhaustive-match warning) |

**Result:** every row ✅. No ⚠️ amend, no ❌ fix. Status flipped to Accepted; the
plan-phase and acceptance-criteria provenance markers were stripped from the
ADR body in the same pass (the ADR now reads as the current design record).
Suite green both platforms.
