# Architecture Overview

Entry point for newcomers. Complements the ADRs with a structural map
of the codebase and the integration flow between layers.

---

## Package Map

```
core/src/main/scala/
├── logic/           Formula, Term, FOL, Model, Valuation
├── parser/          Combinators, FormulaParser, FOLAtomParser, TermParser, FOLParser,
│                    ParseError, SyntaxFailure, SourcePosition
├── lexer/           Lexer (List[Char] → List[Token]), Token, TokenKind, TokenText, LexFailure
├── semantics/       FOLSemantics
├── printer/         FOLPrinter, PrinterUtil
├── util/            StringUtil
│
└── vql/             Vague quantifier extension (imports foundation ↑, never imported by it)
    ├── parser/      VagueQueryParser
    ├── logic/       ParsedQuery
    ├── typed/       TypeCatalog, QueryBinder, BoundQuery, TypedSemantics,
    │                RuntimeModel, RuntimeDispatcher, FolModel, Value,
    │                TypeDefs, Extract, LiteralParser
    ├── semantics/   VagueSemantics (typed facade)
    ├── sampling/    HDRSampler, ProportionEstimator, SampleSizeCalculator, NormalApprox, SamplingParams
    ├── quantifier/  VagueQuantifier, Quantifier
    ├── fragment/    Fragment, FragmentViolation, FragmentCheck
    ├── result/      VagueQueryResult, EvaluationOutput
    ├── error/       QueryError, QueryException, ParseRefusal, TypeCheckError,
    │                RuntimeModelError
    └── id/          TypeId, SymbolName (imports nothing; vql.error and vql.typed both import it)
```

---

## Layer Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│  PUBLIC API                                                     │
│  VagueQueryParser.parse(s)       → Either[QueryError, ParsedQuery]│
│  FolModel(catalog, runtimeModel) → Either[QueryError, FolModel]  │
│  VagueSemantics.evaluateTyped(q, folModel, …)                   │
│                                  → Either[QueryError, Output]    │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────┼────────────────────────────────────┐
│  TYPED IL: BoundQuery      │                                    │
│  QueryBinder.bind(query, catalog) → Either[…, BoundQuery]        │
│  ┌─────────────────────────┴──────────────────────────────────┐ │
│  │ quantifier: Quantifier                                     │ │
│  │ variable:   BoundVar (name, sort)                          │ │
│  │ range:      BoundFormula                                   │ │
│  │ scope:      BoundFormula                                   │ │
│  │ answerVars: List[BoundVar]                                 │ │
│  └────────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────┼────────────────────────────────────┐
│  EVALUATION PIPELINE       │                                    │
│                            ▼                                    │
│  TypedSemantics.evaluate(boundQuery, runtimeModel, params)      │
│    ├── collectRangeElements   enumerate sort domain → D_R        │
│    ├── RuntimeDispatcher       decide predicates / functions     │
│    ├── ProportionEstimator     sample (or exact) → estimate      │
│    └── VagueQueryResult.fromEstimate → satisfied: Boolean        │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────┼────────────────────────────────────┐
│  FOL FOUNDATION            │                                    │
│                            ▼                                    │
│  FOLSemantics.holds(formula, model, valuation) → Boolean        │
│  FormulaParser.parse(atomParser)(tokens) → (Formula, remaining) │
│  FOLPrinter.printFormula(formula) → String                      │
│  Model[D](domain, interpretation)                               │
└─────────────────────────────────────────────────────────────────┘
```

---

## Evaluation Pipeline

A parsed query flows through the typed pipeline in three stages:

| Stage | Step |
|---|---|
| Parse | `VagueQueryParser.parse(s)` → `ParsedQuery`. A tree is built through the checked building functions (`Term.mkVar`, `FOL(…)`, `Formula.mkForall`, …), so every name in it is one query text can produce |
| Bind | `VagueSemantics.bindTyped(query, catalog)` → `BoundQuery` (via `QueryBinder.bind`) |
| Evaluate | `VagueSemantics.evaluateTyped(query, folModel, …)` → `EvaluationOutput[Value]` |

See [ADR-001](ADR-001.md) for the full trace diagram.

---

## Key Integration Points

**Type catalog and model construction:**
`TypeCatalog` declares sorts (`DomainType` / `ValueType`) and symbol
signatures. `RuntimeModel` supplies each sort's domain and a
`RuntimeDispatcher` that decides predicates and functions. `FolModel`
validates a catalog/model pair (dispatcher coverage + domain registration)
and is the pre-validated input to evaluation. See [ADR-001](ADR-001.md).

**Symbol resolution:**
`RuntimeDispatcher` resolves every predicate and function by name against
consumer-supplied logic, returning `Either[String, Boolean]` /
`Either[String, Any]`. There is no separate model-augmentation step: a
consumer implements comparisons, arithmetic, and domain functions directly
in its dispatcher.

**Range extraction:**
`TypedSemantics.collectRangeElements` enumerates the quantified variable's
sort domain and keeps the elements satisfying the range formula — the
domain D_R. Extraction is always exact (never sampled).

**Sampling:**
`HDRSampler` (Fisher-Yates + counter-based PRNG) draws scope samples.
`SamplingParams.exact` forces n = N for deterministic full-domain
evaluation. See [ADR-003](ADR-003.md).

**Fragment membership:**
`fragment.FragmentCheck.check(formula, fragment)` decides, structurally, whether
a parsed `Formula[FOL]` lies in a declared `Fragment` (`Targeting` or
`Screening(k)`), returning the first `FragmentViolation` otherwise. It runs on
the parse tree before any typed bind, needs no `TypeCatalog` or model, and forks
no parser. Callers with text parse via `FOLParser.parse` and map the foundation
`ParseError` at their own boundary: `Syntax`, `Trailing` and `Lex` each carry a
typed value and a `SourcePosition`. See [ADR-018](ADR-018.md).

**Error handling:**
Public methods return `Either[QueryError, A]`. Every refusal is a case of a
closed `enum` with a derived `message` (ADR-022), and each error is declared
once in `vql.error` (ADR-023). See [ADR-002](ADR-002.md) and
[ADR-012](ADR-012.md).

---

## Architecture Decision Records

| ADR | Topic |
|---|---|
| [ADR-001](ADR-001.md) | Many-Sorted Query Binding — `BoundQuery` typed IL, catalog, binder, typed evaluator |
| [ADR-002](ADR-002.md) | Parser-Combinator Style — CPS, single Either boundary |
| [ADR-003](ADR-003.md) | HDR Deterministic Sampling — Fisher-Yates, reproducibility |
| [ADR-004](ADR-004.md) | Tagless Initial Architecture — ADTs + operations, layering |
| [ADR-006](ADR-006.md) | ADT Encoding — `enum` for pure-data sum types, `sealed trait` for behavioural hierarchies |
| [ADR-007](ADR-007.md) | Preserve OCaml-Ported Parser Combinator Core |
| [ADR-012](ADR-012.md) | Error Channel Policy — `require` vs `Either` |
| [ADR-014](ADR-014.md) | Domain Type Quantifiability — two orthogonal validation checks |
| [ADR-015](ADR-015.md) | Symmetric Value Boundaries — `LiteralParser` / `Extract` typeclasses |
| [ADR-016](ADR-016-carrier-witness-on-symmetric-value-typeclasses.md) | Carrier Witness on Symmetric Value Typeclasses (Proposed) |
| [ADR-017](ADR-017.md) | Formula Ranges and the Satisfying-Set Boundary |
| [ADR-018](ADR-018.md) | Structural Fragment Membership over the Parse Tree |
| [ADR-019](ADR-019.md) | Structured Bind-Error Detail across the Error/Typed Boundary (Superseded by ADR-023) |
| [ADR-020](ADR-020.md) | Binder Error Accumulation across Independent Subtrees |
| [ADR-021](ADR-021.md) | Structured Bind-Error Detail for Every `TypeCheckError` Variant (Superseded by ADR-023) |
| [ADR-022](ADR-022.md) | Typed Refusals (Proposed) |
| [ADR-023](ADR-023.md) | Errors Declared Once above a Shared Identifier Package (Proposed) |

---

## Build

Scala 3.8.4, sbt 1.12.15, GraalVM Java 25.

No external runtime dependencies beyond `com.risquanter::hdr-rng`.
Test framework: munit 1.0.4.

```
sbt test          # the whole suite, JVM and Scala.js
sbt publishLocal  # com.risquanter::vql-engine at the version in build.sbt
```

---

## References

- Fermüller, C. G. et al. (2016). "Querying with Vague Quantifiers Using Probabilistic Semantics". *Int. J. Intelligent Systems*, 31(12).
- Harrison, J. (2009). *Handbook of Practical Logic and Automated Reasoning*. Cambridge University Press.
