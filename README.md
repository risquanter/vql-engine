# vql-engine

A Scala 3 first-order logic engine with **vague quantifiers** ("about half", "most", "at least 3/4"), cross-built for the JVM and Scala.js.

The FOL foundation (parser combinators, pretty printer, Tarski semantics) follows John Harrison's *Handbook of Practical Logic and Automated Reasoning*; the vague-quantifier layer implements the probabilistic semantics of Fermüller, Hofer & Ortiz (2016). Sampling-based evaluation uses [hdr-rng](https://github.com/risquanter/hdr-rng) for deterministic, reproducible draws.

## Installation

```scala
libraryDependencies += "com.risquanter" %%% "vql-engine" % "0.14.0"
```

(`%%%` resolves to `vql-engine_3` on the JVM and `vql-engine_sjs1_3` on Scala.js; use `%%` for JVM-only projects.)

## Quick start

Parse a query in the paper's syntax and evaluate it through the typed, many-sorted pipeline. Declare the sorts and predicate signatures in a `TypeCatalog`, provide the runtime data (domains plus a dispatcher that decides each predicate) in a `RuntimeModel`, and combine them into a validated `FolModel`. The public API is `Either`-based: parse, model-construction, and evaluation errors come back as typed `QueryError` values, never exceptions.

```scala
import vql.parser.VagueQueryParser
import vql.semantics.VagueSemantics
import vql.sampling.SamplingParams
import vql.typed.{FolModel, RuntimeModel, RuntimeDispatcher, TypeCatalog, PredicateSig, TypeId, SymbolName, Value}
import vql.typed.TypeDecl.DomainType

// 1. Declare the sort and predicate signatures.
val asset = TypeId("Asset")
val catalog = TypeCatalog.unsafe(
  types = Set(DomainType(asset)),
  predicates = Map(
    SymbolName("monitored") -> PredicateSig(List(asset)),
    SymbolName("critical")  -> PredicateSig(List(asset))
  )
)

// 2. Provide the runtime data: the domain of each sort plus a dispatcher.
val values = Set(Value(asset, "a1"), Value(asset, "a2"), Value(asset, "a3"), Value(asset, "a4"))
val dispatcher = new RuntimeDispatcher:
  def evalPredicate(name: SymbolName, args: List[Value]): Either[String, Boolean] =
    name.value match
      case "monitored" => Right(true)
      case "critical"  => Right(args.headOption.exists(v => v.raw == "a1" || v.raw == "a2" || v.raw == "a3"))
      case other       => Left(s"no predicate: $other")
  def evalFunction(name: SymbolName, args: List[Value]): Either[String, Any] =
    Left(s"no function: ${name.value}")
  def predicateSymbols: Set[SymbolName] = Set(SymbolName("monitored"), SymbolName("critical"))
  def functionSymbols: Set[SymbolName] = Set.empty

val runtimeModel = RuntimeModel(domains = Map(asset -> values), dispatcher = dispatcher)

// 3. Parse, build the model, evaluate — every step returns Either[QueryError, _].
// "At least 3/4 of monitored assets are critical"
val queryStr = """Q[>=]^{3/4} x (monitored(x), critical(x))"""

val outcome = for
  query    <- VagueQueryParser.parse(queryStr)
  folModel <- FolModel(catalog, runtimeModel)
  output   <- VagueSemantics.evaluateTyped(query, folModel, samplingParams = SamplingParams.exact)
yield output

outcome match
  case Right(o) => println(s"satisfied=${o.satisfied} proportion=${o.proportion} (${o.satisfyingElements.size}/${o.rangeElements.size})")
  case Left(e)  => println(e.formatted)
```

Supported quantifier operators: About (`~`), AtLeast (`>=`), AtMost (`<=`), each with a proportion `k/n` and an optional custom tolerance (`Q[~]^{1/2}[0.05]`). Queries with answer variables return answer sets; queries without are Boolean.

The range `R(x, y')` is a full FOL formula, so the quantified population can be a compound set — `Q[<=]^{1/3} x (server(x) /\ ~patched(x), exploitable(x))`. Negation is closed-world over the sort's active domain (see [docs/VagueQuantifiers.md](docs/VagueQuantifiers.md) and [docs/ADR-017.md](docs/ADR-017.md)). A separate `VagueSemantics.satisfyingSet(formula, variable, folModel)` entry point evaluates a bare single-free-variable formula to its exact satisfying set, with no quantifier or sampling.

For consumers that admit only a restricted sub-language, `vql.fragment.FragmentCheck.check(formula, fragment)` tests a parsed `Formula[FOL]` for structural membership in a `Fragment` (`Targeting` — no quantifiers or function applications; `Screening(k)` — quantifier nesting depth ≤ k), returning the first `FragmentViolation` otherwise. It runs on the parse tree, needs no model, and forks no parser (see [docs/ADR-018.md](docs/ADR-018.md)).

## Architecture

Two layers in `core/src/main/scala`:

- **FOL foundation** (`logic`, `lexer`, `parser`, `printer`, `semantics`, `util`): terms, formulas, parser combinators with precedence and associativity, pretty printing with round-trip fidelity, Tarski model semantics.
- **Vague quantifier extension** (`vql.*`): the query parser (`vql.parser`), the typed intermediate representation and pipeline (`vql.typed`: `TypeCatalog`, `QueryBinder` → `BoundQuery`, `TypedSemantics`, `FolModel`, `RuntimeModel`), HDR-based samplers with confidence intervals (`vql.sampling`), the structural fragment-membership check (`vql.fragment`), the `VagueSemantics` facade, and typed `QueryError`s (`vql.error`). The extension imports the foundation, never the reverse.

[docs/Architecture.md](docs/Architecture.md) has the full package map, layer diagram, and integration flow.

## Tests

```bash
sbt +test    # full suite, JVM + Scala.js
```

`core/src/test/scala/vql/semantics/VagueSemanticsTypedSpec.scala` exercises the typed pipeline end to end and is the most complete worked example of the API above. For a runnable walkthrough, `sbt "folEngineJVM/runMain examples.VagueDemo"` runs `examples.VagueDemo` (plain query, compound range with negation, and `satisfyingSet`).

## Documentation

- [docs/Architecture.md](docs/Architecture.md) — structural map and integration flow
- [docs/VagueQuantifiers.md](docs/VagueQuantifiers.md) — theory, semantics, API reference
- [docs/](docs/) — ADRs recording design decisions
- [docs/RELEASE.md](docs/RELEASE.md) — CI, signing, and release procedure
- [CHANGELOG.md](CHANGELOG.md) — released versions and breaking changes

## Release verification

Artifacts on Maven Central are GPG-signed (key `0F0D975BADB0C1F45F5424A20BCC447FF2426979`,
published on `keyserver.ubuntu.com`) and carry Sigstore bundles (`*.sigstore.json`) bound to this
repository's CI workflow identity:

```
cosign verify-blob --bundle vql-engine_3-<version>.jar.sigstore.json \
  --certificate-identity-regexp="https://github.com/risquanter/vql-engine/.github/workflows/ci-build.yml@refs/heads/main" \
  --certificate-oidc-issuer=https://token.actions.githubusercontent.com \
  vql-engine_3-<version>.jar
```

## License

Apache License 2.0 — see [LICENSE.md](LICENSE.md).

## References

1. **Harrison, J.** (2009). *Handbook of Practical Logic and Automated Reasoning*. Cambridge University Press. ISBN 978-0-521-89957-4.
2. **Fermüller, C. G., Hofer, M., & Ortiz, M.** (2016). Querying with Vague Quantifiers Using Probabilistic Semantics. *CIKM 2016*.
