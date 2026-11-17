# Review records — branch `main`

```
COVERAGE  tier=routine  scope=the open changes on main (File scope source 2):
          the working tree against HEAD (CHANGELOG.md, build.sbt) and the
          commits 7a80cd1, 8f07978, b98283a that main carries beyond origin/main
read-in-full:
  .claude/skills/vql-code-quality-review/SKILL.md
  .claude/skills/vql-supply-chain/SKILL.md
  CHANGELOG.md
  CLAUDE.md
  build.sbt
  core/src/main/scala/vql/error/TypeCatalogError.scala
  core/src/main/scala/vql/typed/TypeCatalog.scala
  core/src/test/scala/vql/typed/TypeCatalogSpec.scala
  docs/ADR-006.md
  docs/ADR-023.md
  docs/Architecture.md
  docs/PLAN-catalog-errors-in-vql-error.md
  docs/PLAN-node-returning-functions.md
  docs/PLAN-predicate-satisfying-set.md
  docs/TODOS.md
  docs/reviews/README.md
  also read: docs/WORKING-INSTRUCTIONS.md, docs/ADR-002.md, docs/ADR-004.md,
    docs/ADR-012.md, docs/ADR-020.md, docs/ADR-022.md
discharged:
  1 plan conformance — 1 finding reported (build.sbt absent from the CEIVE
    file inventory) — core/src/main/scala/vql/error/TypeCatalogError.scala:1-33
    matches PLAN-catalog-errors-in-vql-error.md §5.1 verbatim;
    core/src/main/scala/vql/typed/TypeCatalog.scala:4, :62-64, :84-86 match §5.2;
    TypeCatalogSpec.scala:5 and the three tests at :203-238 match §6 Phase 1 and §7;
    ADR-023.md:50-51 and :163, ADR-006.md:181, Architecture.md:31-32,
    CHANGELOG.md:6-19, vql-code-quality-review SKILL.md:86 and
    PLAN-node-returning-functions.md:83 and :248 match §6 Phase 2 steps 1-6
  2 family search — PASS —
    `grep -rn "enum .*\(Error\|Failure\|Refusal\|Problem\)" core/src/main/scala`
      quoted core/src/main/scala/vql/error/RuntimeModelError.scala:9 `enum RuntimeModelError:`;
      TypeCatalogError joins the vql.error failure family, pattern <Subject>Error
      with a derived message
    `grep -rn "enum .*Site" core/src/main/scala`
      quoted core/src/main/scala/vql/error/TypeCheckError.scala:7 `enum MismatchSite:`;
      DeclarationSite joins the <Thing>Site family with a derived label,
      declared in the file of the error that holds it
    `grep -rn "def message\|def label" core/src/main/scala/vql/error`
      quoted RuntimeModelError.scala:23 `def message: String = this match`;
      TypeCatalogError.message follows it
    no duplicate: no other enum in vql.error names catalog declaration slots
  3 mechanical bug scan — PASS — TypeCatalog.scala:62-64 (Nil / errors arms),
    :84-86 (message join), TypeCatalogError.scala:12-17, :28-31 (every case matched)
  4 convention conformance — PASS — `grep -rn "import vql.typed" core/src/main/scala/vql/error`
    prints nothing (ADR-004 §2, ADR-023 §4); TypeCatalogError.scala:3 named top-level
    import (ADR-006 §4-§5); both enums pure data (ADR-006 §1, §3); scaladoc at
    TypeCatalogError.scala:5, :19, :21, :24, :27 states current behaviour
  5 test presence — PASS after a pinning test was added at TypeCatalogSpec.scala:240-258
    for the "; " separator; no assertion weakened (git diff origin/main of the spec
    adds lines only, besides the import); sbt test passes on the JVM and on Node
  6 drive-by changes — PASS — 8f07978 and b98283a are separate themed commits of
    documents only; the working tree holds the version line and the changelog order
  7 boyscout pass — 1 finding reported, pre-existing (docs/Architecture.md:171, :174)
  8 terminology freshness (name level) — discharged: no glossary
  PSS plan, plan gate only — 1 finding reported (gate items 1 and 3)
escalated:
  core/src/main/scala/vql/typed/TypeCatalog.scala:84-86 — the text of the
    IllegalArgumentException TypeCatalog.unsafe throws changes from toString
    output joined by ", " to message sentences joined by "; " (changed error-path
    semantics) → complex
  core/src/main/scala/vql/error/TypeCatalogError.scala — two public types change
    package with no alias; the plan's §10 claim that register names neither is
    unverified at this tier (wire contracts: public API shape) → complex
  build.sbt:4 — a dependency manifest changes; the hunk is the version line only
    (a dependency manifest or a pinned version) → security
  core/src/main/scala/vql/error/TypeCatalogError.scala:13 —
    DeclarationSite.Constant(name: String) text now reaches the exception
    message through message; whether a consumer's constant keys can hold
    caller text is unverified (security 7, refusal messages that quote a
    caller's text) → security
```

```
COVERAGE  tier=complex  scope=the routine tier's two complex escalations on main
          (File scope source 2: the commits 7a80cd1, 0cf7708, 58b81ba, 5652015,
          8f07978, b98283a beyond origin/main): TypeCatalog.scala:84-86 and the
          move of TypeCatalogError and DeclarationSite to vql.error
read-in-full:
  core/src/main/scala/vql/typed/TypeCatalog.scala
  core/src/main/scala/vql/error/TypeCatalogError.scala
  core/src/main/scala/vql/error/QueryError.scala
  core/src/main/scala/vql/error/TypeCheckError.scala
  core/src/main/scala/vql/error/RuntimeModelError.scala
  core/src/main/scala/vql/error/ParseRefusal.scala
  core/src/main/scala/vql/typed/TypeDefs.scala
  core/src/main/scala/vql/typed/RuntimeModel.scala
  core/src/main/scala/vql/typed/FolModel.scala
  core/src/main/scala/vql/semantics/VagueSemantics.scala
  core/src/test/scala/vql/typed/TypeCatalogSpec.scala
  docs/PLAN-catalog-errors-in-vql-error.md
  docs/ADR-001.md, docs/ADR-004.md, docs/ADR-006.md, docs/ADR-012.md, docs/ADR-023.md
  CLAUDE.md, CHANGELOG.md (0.22.0)
  register: modules/common/src/main/scala/com/risquanter/register/common/FolSymbols.scala:200-262
  register: modules/server/src/main/scala/com/risquanter/register/foladapter/RiskTreeKnowledgeBase.scala:340-420
  register: modules/server/src/main/scala/com/risquanter/register/services/QueryServiceLive.scala:60-120
  register: modules/server/src/main/scala/com/risquanter/register/services/cache/MitigationScopeResolver.scala:60-100
  register: modules/server/src/main/scala/com/risquanter/register/http/DefectCatchAllInterceptor.scala
  examples/ does not exist; core/src/main/scala/examples/VagueDemo.scala calls unsafe only
discharged:
  1 adversarial line scan — PASS — TypeCatalogError.scala:12-17 and :28-31 match
    every case, so message cannot throw; TypeCatalog.scala:62-64 keeps apply's
    Right/Left split; :84-86 changes the exception text only
  2 removed-behaviour audit — PASS — every field the old toString printed
    (type, site kind, symbol, parameter kind, index) is in the new sentence via
    DeclarationSite.label, TypeCatalogError.scala:12-17; error order unchanged,
    collectErrors TypeCatalog.scala:90-141 is untouched
  3 cross-module tracing — PASS —
    exception text: no reader. register calls unsafe at FolSymbols.scala:248
      (object val, fixed signatures) and RiskTreeKnowledgeBase.scala:387 (inside
      schemaFor, reached from QueryServiceLive.scala:88 and
      MitigationScopeResolver.scala:83). A throw there becomes a ZIO defect,
      DefectCatchAllInterceptor.scala:115-125 logs the cause and answers
      WireError.InternalFault(trace) with no text. No register test, record or
      plan reads "Invalid TypeCatalog".
    package move: `grep -rn "TypeCatalogError\|DeclarationSite"` over register
      modules/ prints nothing; register's vql.typed imports are named, no wildcard.
      Docs mention only removed or point-in-time content (docs/dev/TODO.md:2845-2847,
      docs/dev/plans/PLAN-RISKTRANSFORM.md:4937-4938). Plan §10 holds.
    facade: VagueSemantics.scala builds no catalog and surfaces no TypeCatalogError
    validation boundary for the security tier: TypeCatalog.apply ->
      collectErrors at TypeCatalog.scala:62, reached from unsafe at :81; register's
      constant keys come from ValuationBasis symbols at FolSymbols.scala:222 and its
      validator keys are fixed sorts at RiskTreeKnowledgeBase.scala:378-385
  4 state and interleaving — discharged: the library holds no shared mutable state
  5 invariant reconstruction — PASS — valid-by-construction catalog
    (TypeCatalog.scala:19 private constructor); vql.error imports no vql.typed
    (TypeCatalogError.scala:3); one declaration per type; deterministic error order
  6 design altitude — PASS; if/else check PASS — the ifs at TypeCatalog.scala:101,
    :107, :113, :122, :131 are single membership predicates
  7 terminology freshness (meaning level) — discharged: no glossary
  8 inline verification — 1 finding kept, CONFIRMED (docs/ADR-004.md:67-69)
  sbt test — pass, JVM and Node
escalated:
  none
```
