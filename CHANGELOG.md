# Changelog

This project follows early-semver (pre-1.0): breaking changes bump the minor
version.

## 0.11.0

### Added

- **Formula ranges.** The range `R(x, y')` of a vague query is now a full FOL
  formula, not a single atom. The quantified population may be a compound set
  built from `/\`, `\/`, `~`, and inner quantifiers, e.g.
  `Q[<=]^{1/3} x (server(x) /\ ~patched(x), exploitable(x))`. Negation is a
  closed-world complement over the sort's active domain; range extraction stays
  exhaustive (never sampled). See ADR-017.
- **`VagueSemantics.satisfyingSet(formula, variable, folModel)`** — a new entry
  point that evaluates a bare formula with one free variable to its exact
  satisfying set over that variable's sort, with no quantifier and no sampling.
  Returns `Either[QueryError, Set[Value]]`.

### Changed (breaking)

- **Untyped evaluation backend removed.** `VagueSemantics.holds` /
  `VagueSemantics.evaluate`, `RangeExtractor`, and the `fol.datastore`
  package (`KnowledgeBase` / `KnowledgeSource` / `DomainCodec` and
  `RelationName`) are deleted. The typed many-sorted pipeline
  (`VagueSemantics.evaluateTyped` → `fol.typed`) is the only evaluation path.
- **Four `QueryError` variants removed** with the untyped datastore:
  `RelationNotFoundError`, `SchemaError`, `PositionOutOfBoundsError`,
  `DataStoreError`. These were raised only by the deleted backend.
- **`parser.FOLParser.{parse, defaultParser, parseWithLexer}`** now return
  `Either[parser.ParseError, Formula[FOL]]` instead of throwing.
  `parser.ParseError` is a foundation-local error type.
- **Range type widened in the IL and parse tree.** `BoundQuery.range` is now
  `BoundFormula` (was `BoundAtom`) and `ParsedQuery.range` is now
  `Formula[FOL]` (was `FOL`). A single-atom range is `Formula.Atom(fol)` /
  `BoundFormula.Atom(atom)`. Direct case-class construction of `ParsedQuery`
  with a bare `FOL` no longer compiles; `ParsedQuery.mk` keeps a
  source-compatible overload that accepts a bare atom and wraps it. Consumers
  that reach the engine through `VagueQueryParser.parse` and
  `VagueSemantics.evaluateTyped` are unaffected.
- **`fol.error.ErrorOps` removed** — an unused error-handling helper object
  (`attempt`, `fromThrowable`, `validate`, `require`, `fromOption`). It had no
  caller. The public API is `Either`-returning; lift throwables at your own
  boundary if needed.
