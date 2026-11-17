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
