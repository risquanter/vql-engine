# External-API `Either` audit — 2026-08-10

Cross-cutting verification (`PLAN-range-formula-and-satisfying-set.md` §10.1)
that after all phases the external-facing API is `Either`-based and no
vague-layer code relies on the exception form on any live path. Run against the
`0.11.0` source. Suite green both platforms.

## Check 1 — external entry points return `Either`

Verified against the actual signatures:

| Entry point | Return | Verdict |
|---|---|---|
| `parser.FOLParser.{parse, defaultParser, parseWithLexer}` | `Either[parser.ParseError, Formula[FOL]]` | ✅ (`FOLParser.scala:28,32,44`) |
| `fol.parser.VagueQueryParser.parse` | `Either[QueryError, ParsedQuery]` | ✅ (`VagueQueryParser.scala:34`) |
| `fol.semantics.VagueSemantics.{bindTyped, evaluateTyped, satisfyingSet}` | `Either[QueryError, A]` | ✅ |
| `fol.typed.QueryBinder.{bind, bindSatisfyingFormula}` | `Either[List[TypeCheckError], A]` | ✅ (`QueryBinder.scala:10,38`) |
| `fol.typed.TypedSemantics.{evaluate, satisfyingSet}` | `Either[QueryError, A]` | ✅ (`TypedSemantics.scala:12,38`) |
| `fol.typed.TypeCatalog.apply` | `Either[List[TypeCatalogError], TypeCatalog]` | ✅ |
| `fol.typed.FolModel.apply` | `Either[QueryError, FolModel]` | ✅ (`FolModel.scala:27`) |

## Check 2 — no vague-layer reliance on the exception form

Greps over `core/src/main/scala/fol/**`.

### 2a — `catch` of `QueryException`

Live catch sites: exactly one — `VagueQueryParser.parse`
(`VagueQueryParser.scala:51`), the allowlisted vague boundary that catches the
internal-combinator / `ParsedQuery.mk` throws once and returns `Either`.

The audit first found a second catch in `fol.error.ErrorOps.attempt`, but
`ErrorOps` had zero callers in this repo and in register (the only downstream
consumer). It was removed in 0.11.0 (recorded in `CHANGELOG.md`), so the
allowlisted parse boundary is now the only `QueryException` catch.

### 2b — call to a throwing `FOLParser` form

Zero. The only `FOLParser.` occurrence in vague main is a scaladoc reference
(`VagueSemantics.scala:99`), not a call. The `Either`-returning form is the only
public form.

### 2c — remaining `throw` / `QueryException(` raise sites

Every site lies in the allowlist:

| Site | Allowlist rationale |
|---|---|
| `ParsedQuery.mk` (`ParsedQuery.scala:95,107`) | ADR-002 combinator style; thrown, caught once at `VagueQueryParser.parse` |
| `VagueQueryParser` internal combinators (`VagueQueryParser.scala:136–253`) | same; caught at `parse` |
| `TypeCatalog.unsafe` (`TypeCatalog.scala:84`) | tests/startup-only; a catalog inconsistency is a programming error; `Either` sibling is `TypeCatalog.apply` |
| `QueryError.toThrowable` / `QueryException` def (`QueryError.scala:42,267`) | the throwable bridge/definition, not a raise on a consumer path |

## Result

Check 1 fully `Either`. Check 2 live sites are all allowlisted; the single
non-allowlisted catch (`ErrorOps.attempt`) is provably dead code and reaches no
consumer path. Audit passes.
