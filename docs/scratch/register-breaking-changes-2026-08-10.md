# vql-engine breaking changes for register — handoff

**Date:** 2026-08-10
**vql-engine version:** upgrading to `0.11.0` (from `0.10.2`; breaking, pre-1.0
minor bump). Register should pin `0.11.0` when adapting.
**Scope:** what changed in vql-engine that register (the only downstream
consumer) must adapt to. Written to be handed to the register agent as-is.

All references below are register paths under `modules/`. Line numbers are as
of 2026-08-10 and may drift.

---

## 1. Removed `QueryError` variants — register `AppError.scala` breaks (MUST FIX)

vql-engine deleted its untyped evaluation backend (T-006). That removed the
`fol.datastore.RelationName` type, which forced removal of four `QueryError`
variants. Register's error mapping matches all four, so
`common/.../domain/errors/AppError.scala` will not compile against `0.11.0`.

Removed variants and the register `case` arms that must go:

| Removed `QueryError` variant | Register `AppError.scala` arm | Notes |
|---|---|---|
| `RelationNotFoundError` | line ~390 `case e: QE.RelationNotFoundError => FolUnknownSymbol(...)` | used `RelationName` |
| `SchemaError` | line ~392 `case e: QE.SchemaError => FolUnknownSymbol(...)` | used `RelationName` |
| `PositionOutOfBoundsError` | line ~411 `case e: QE.PositionOutOfBoundsError => FolEvaluationFailure(..., "position_bounds")` | used `RelationName` |
| `DataStoreError` | line ~410 `case e: QE.DataStoreError => FolEvaluationFailure(..., "data_store")` | untyped datastore error |

Register actions:
- Delete those four `case` arms from the `QueryError => FolQueryFailure` mapping.
- Update the scaladoc at `AppError.scala` ~line 253 that names
  `RelationNotFoundError` / `UninterpretedSymbolError` / `SchemaError`.

These variants were only ever raised by the deleted untyped datastore
(`KnowledgeBase`), never by the typed pipeline register actually uses, so
dropping the arms loses no reachable behavior.

---

## 2. Removed `fol.datastore.RelationName` — register test breaks (MUST FIX)

`server/.../domain/errors/FolQueryFailureFromQueryErrorSpec.scala`:
- line 6 `import fol.datastore.RelationName` — remove (type deleted).
- Delete the four test cases that construct the removed variants:
  `RelationNotFoundError` (~56–59), `SchemaError` (~73–74),
  `DataStoreError` (~213–214), `PositionOutOfBoundsError` (~220–221).

Note: register's own `RiskTreeKnowledgeBase` class is unrelated to the deleted
`fol.datastore.KnowledgeBase` — it stays.

---

## 3. Removed untyped `VagueSemantics.holds` / `evaluate` — NO register action

Register evaluates only via the typed path (`VagueSemantics.evaluateTyped` →
`fol.typed`), confirmed in `QueryServiceLive` / `QueryService`. The removed
untyped entry points have no register caller.

---

## 4. `FOLParser` now returns `Either` — NO register action yet

`parser.FOLParser.{parse, defaultParser, parseWithLexer}` now return
`Either[parser.ParseError, Formula[FOL]]` instead of throwing. Register does
not call `FOLParser` directly today, so nothing breaks now. When register adds
targeting-predicate parsing, it maps `parser.ParseError` at its own boundary:

```scala
FOLParser.parse(raw).left.map {
  case parser.ParseError.Syntax(d)   => AppError.badRequest(...)
  case parser.ParseError.Trailing(r) => AppError.badRequest(...)
  case parser.ParseError.Lex(d)      => AppError.badRequest(...)
}
```

`parser.ParseError` is foundation-local (no `fol.*` import). Note there are two
`ParseError` types now: `parser.ParseError` (foundation, above) and
`fol.error.QueryError.ParseError` (vague layer, unchanged) — do not confuse.

---

## 5. FUTURE (not shipped in 0.11.0): dead `QueryError` variant prune (vql T-008)

A later vql-engine task (T-008) will remove 10 `QueryError` variants the engine
never raises. Register's `AppError.scala` currently maps **all 10**, and the
spec constructs several, so this will be a second coordinated break. Do NOT act
until vql-engine ships it; listed here for planning.

Variants slated for removal and their register `AppError.scala` arms:

| Variant | AppError arm (approx line) |
|---|---|
| `LexicalError` | 388 |
| `UninterpretedSymbolError` | 391 |
| `ScopeEvaluationError` | 403 |
| `TypeMismatchError` | 404 |
| `TimeoutError` | 405 |
| `QuantifierError` | 406 |
| `QueryStructureError` | 409 |
| `ResourceError` | 413 |
| `ConnectionError` | 414 |
| `ConfigError` | 415 |

Plus the matching cases in `FolQueryFailureFromQueryErrorSpec.scala`
(~45, 67, 160, 167, 174, 181, 200, 207, 235, 242).

When T-008 lands, register removes those arms + tests in the same upgrade.

---

## 6. Range widened to a formula (`BoundQuery.range`, `ParsedQuery.range`) — NO register action

The range of a vague query is now a full FOL formula, not a single atom:

- `BoundQuery.range: BoundAtom → BoundFormula` (a single-atom range is
  `BoundFormula.Atom`).
- `ParsedQuery.range: FOL → Formula[FOL]` (a single-atom range is
  `Formula.Atom(fol)`). Direct case-class construction with a bare `FOL` is now
  a compile error; the `ParsedQuery.mk` helper keeps a source-compatible
  overload accepting a bare `FOL` and wrapping it in `Formula.Atom`.

Both are source-breaking only for a consumer that constructs or pattern-matches
`BoundQuery` / `ParsedQuery` directly. Register does neither — it reaches the
engine through `VagueQueryParser.parse` and `VagueSemantics.evaluateTyped`, and
its one `QueryBinder.bind` test call uses the result opaquely — so nothing
breaks.

Listed here for the 0.11.0 changelog (AC-10): both range type changes are
source-breaking for direct IR consumers and must appear in the changelog.
