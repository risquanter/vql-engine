# Changelog

This project follows early-semver (pre-1.0): breaking changes bump the minor
version.

## 0.15.0

### Changed (breaking)

- **`QueryError.BindError` now carries structured detail instead of rendered
  strings.** Its single field changes from `errors: List[String]` to
  `details: List[BindErrorDetail]`. The text contract is preserved as a derived
  `messages: List[String]` (and `message` / `context` read from it), so a
  text-only consumer changes `e.errors` to `e.messages`. Construction and
  pattern matches over the old `errors` field must be updated.

### Added

- **`vql.error.BindErrorDetail`** — a per-error, primitives-only detail for bind
  failures (ADR-019). Cases: `UnparseableConstant(name, sortName, sourceText,
  rendered)` and `Other(rendered)`. `sortName` exposes the sort the failed token
  was expected to be (the opaque `TypeId` crosses the error/typed boundary as its
  `TypeId.value` `String`), letting a consumer tell a mistyped reference from a
  genuine type error without dropping to `QueryBinder.bind` or re-rendering the
  message. The error layer still imports nothing from `vql.typed`.

## 0.14.0

### Changed (breaking)

- **Ten unraised `QueryError` variants removed** (T-008): `LexicalError`,
  `QueryStructureError`, `QuantifierError`, `ScopeEvaluationError`,
  `UninterpretedSymbolError`, `TypeMismatchError`, `ResourceError`,
  `ConnectionError`, `TimeoutError`, `ConfigError`. These were generic
  scaffolding raised by no engine code; the sealed `QueryError` surface now
  reflects only what the typed pipeline returns. The eight live variants
  (`ParseError`, `ValidationError`, `EvaluationError`, `DomainNotFoundError`,
  `UnknownConstantOrLiteralError`, `BindError`, `ModelValidationError`,
  `UnboundVariableError`) are unchanged. Downstream `match` arms over the
  removed variants must be deleted.

### Added

- **Runnable typed-path demo** (`examples.VagueDemo`, T-007): a `@main` that
  builds a `TypeCatalog` → `RuntimeModel` → `FolModel` and runs a plain query,
  a compound-range query with closed-world negation, and a `satisfyingSet`
  call against the finished API.

## 0.13.1

### Changed (breaking)

- **All `fol.*` packages renamed to `vql.*`.** Every vague-layer package —
  `fol.error`, `fol.fragment`, `fol.logic`, `fol.parser`, `fol.quantifier`,
  `fol.result`, `fol.sampling`, `fol.semantics`, `fol.typed` — now lives under
  `vql.*`. Downstream imports rewrite mechanically (`import fol.X` →
  `import vql.X`); there are no type, signature, or behaviour changes. The
  package namespace now matches the published artifact name (`vql-engine`). The
  FOL foundation packages (`logic`, `parser`, `semantics`, `printer`, `lexer`)
  and every `FOL` identifier are unchanged.

## 0.12.1

- CI pipeline patch (release signing / provenance). No library or API changes.

## 0.12.0

### Added

- **Fragment-membership API.** New package `fol.fragment`: a structural
  membership test over a parsed `Formula[FOL]` that decides whether a formula
  lies inside a declared fragment, returning the first violated rule when it does
  not. `FragmentCheck.check(formula, fragment)` returns
  `Either[FragmentViolation, Unit]`. Two fragments: `Fragment.Targeting` (no
  quantifier nodes, no function-application terms) and `Fragment.Screening(k)`
  (maximum quantifier nesting depth ≤ `k`, 0-indexed). The check walks the parse
  tree only, needs no `TypeCatalog`, and forks no parser. Additive and
  non-breaking. See ADR-018.

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
