# vql-engine

A Scala 3 first-order logic engine with **vague quantifiers** ("about half", "most", "at least 3/4"), cross-built for the JVM and Scala.js.

The FOL foundation (parser combinators, pretty printer, Tarski semantics) follows John Harrison's *Handbook of Practical Logic and Automated Reasoning*; the vague-quantifier layer implements the probabilistic semantics of Fermüller, Hofer & Ortiz (2016). Sampling-based evaluation uses [hdr-rng](https://github.com/risquanter/hdr-rng) for deterministic, reproducible draws.

## Installation

```scala
libraryDependencies += "com.risquanter" %%% "vql-engine" % "0.10.2"
```

(`%%%` resolves to `vql-engine_3` on the JVM and `vql-engine_sjs1_3` on Scala.js; use `%%` for JVM-only projects.)

## Quick start

Parse a query in the paper's syntax and evaluate it against a knowledge source. The public API is `Either`-based: parse and evaluation errors come back as typed `QueryError` values, never exceptions.

```scala
import fol.parser.VagueQueryParser
import fol.semantics.VagueSemantics
import fol.datastore.KnowledgeSource
import fol.examples.CyberSecurityDomain

// "At least 3/4 of assets have critical risks"
val queryStr = """Q[>=]^{3/4} x (asset(x), exists r . (has_risk(x, r) /\ critical_risk(r)))"""

val source = KnowledgeSource.fromKnowledgeBase(CyberSecurityDomain.kb)

val outcome = for
  query  <- VagueQueryParser.parse(queryStr)
  result <- VagueSemantics.holds(query, source)
yield result

outcome match
  case Right(r) => println(s"satisfied=${r.satisfied} proportion=${r.proportion} (${r.satisfyingCount}/${r.domainSize})")
  case Left(e)  => println(e.formatted)
```

Supported quantifier operators: About (`~`), AtLeast (`>=`), AtMost (`<=`), each with a proportion `k/n` and an optional custom tolerance (`Q[~]^{1/2}[0.05]`). Queries with answer variables return answer sets; queries without are Boolean.

## Architecture

Two layers in `core/src/main/scala`:

- **FOL foundation** (`logic`, `lexer`, `parser`, `printer`, `semantics`, `util`): terms, formulas, parser combinators with precedence and associativity, pretty printing with round-trip fidelity, Tarski model semantics.
- **Vague quantifier extension** (`fol.*`): query parser, `ResolvedQuery` intermediate representation, exact and sampling evaluation (`VagueSemantics.holds` / `evaluate`), HDR-based samplers with confidence intervals, a relational `KnowledgeBase[D]` / `KnowledgeSource[D]` datastore, FOL bridge with model augmenters, and typed `QueryError`s. The extension imports the foundation, never the reverse.

[docs/Architecture.md](docs/Architecture.md) has the full package map, layer diagram, and integration flow.

## Tests and examples

```bash
sbt +test                                  # full suite, JVM + Scala.js
sbt "folEngineJVM/runMain fol.examples.demo"   # cybersecurity demo: Boolean, unary, and complex queries
```

Example domain and queries live in `core/src/main/scala/fol/examples/`.

## Documentation

- [docs/Architecture.md](docs/Architecture.md) — structural map and integration flow
- [docs/VagueQuantifiers.md](docs/VagueQuantifiers.md) — theory, semantics, API reference
- [docs/](docs/) — ADRs recording design decisions
- [docs/RELEASE.md](docs/RELEASE.md) — CI, signing, and release procedure

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
