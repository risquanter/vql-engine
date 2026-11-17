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

```
COVERAGE  tier=routine  scope=the open changes on main (File scope source 2):
          git diff origin/main...HEAD, the commits 808c3fd, 6cbb973, ba1ae06 and
          fbcf1f8; the working tree was clean at the start
read-in-full:
  .claude/skills/vql-adr-constraints/SKILL.md
  CHANGELOG.md
  README.md
  build.sbt
  core/.jvm/src/test/scala/parser/NestingBoundStackSpec.scala
  core/src/main/scala/vql/fragment/Fragment.scala
  core/src/main/scala/vql/fragment/FragmentCheck.scala
  core/src/test/scala/vql/fragment/FragmentCheckSpec.scala
  docs/ADR-004.md
  docs/ADR-018.md
  docs/Architecture.md
  docs/PLAN-catalog-errors-in-vql-error.md
  docs/PLAN-targeting-formula-inspection-INVENTORY.md
  docs/PLAN-targeting-formula-inspection.md
  also read: CLAUDE.md, docs/WORKING-INSTRUCTIONS.md,
    .claude/skills/vql-code-quality-review/SKILL.md, docs/ADR-001.md,
    docs/ADR-006.md, docs/ADR-007.md, docs/ADR-012.md, docs/ADR-014.md,
    docs/ADR-022.md, docs/ADR-023.md, docs/ADR-024.md,
    docs/PLAN-catalog-errors-in-vql-error-INVENTORY.md,
    .claude/hooks/protocol-gate.sh, .claude/protocol/approved,
    core/src/main/scala/parser/NestingBound.scala,
    core/src/test/scala/parser/NestingBoundSpec.scala,
    core/src/main/scala/lexer/TokenText.scala (LiteralText, SymbolText),
    core/src/main/scala/parser/TermParser.scala:35-115,
    core/src/main/scala/parser/ParseError.scala:19-38,
    register: docs/dev/decision-records/ADR-029-input-injection-defence.md:89,
    register: TargetingPredicate.scala:146 (the one Targeting construction)
discharged:
  1 plan conformance — PASS — Fragment.scala:19, :41-42 match plan §5.1;
    FragmentCheck.scala:21-24, :37-42, :52-53, :60-124 match §5.2 verbatim;
    FragmentCheckSpec.scala and NestingBoundStackSpec.scala:66-71 match §6
    Phase 1 steps 3 and 4 (no assertion changed); every changed file is a
    bullet of PLAN-targeting-formula-inspection-INVENTORY.md except
    .claude/skills/vql-adr-constraints/SKILL.md, which the plan's File
    inventory section names as outside the gated region; the test at
    FragmentCheckSpec.scala:406 is authorized by plan §4's ADR-024 row
    ("and on Node"); §7 commands print nothing
  2 family search — PASS —
    `grep -rn "enum .*Violation" core/src/main/scala` → Fragment.scala:34
      `enum FragmentViolation:`; PredicateNotAllowed(predicate: SymbolText)
      follows FunctionApplicationNotAllowed(function: SymbolText); nearest other
      case TypeCheckError.UnknownPredicate(name: SymbolText) denotes an
      undeclared name, a different concept
    forbiddenPredicates: Set[SymbolName] follows admittedFunctions:
      Set[SymbolName] in Fragment.scala:19
    `grep -rn "^object [A-Z]" core/src/main/scala/vql` → FragmentCheck.scala:18
      `object FragmentCheck`; violations and literalArguments sit beside
      `def check`
    `grep -rn "toLowerCase" core/src/main/scala` → only FragmentCheck.scala:89;
      no existing lower-casing helper
    `grep -rn "def overatoms|def atomUnion|def predicates|def functions"
      core/src/main/scala` → nothing; no existing atom walk duplicates atomTerms
  3 mechanical bug scan — PASS — check's match on List is exhaustive
    (FragmentCheck.scala:22-24); literalsIn covers Var, Const, Fn (:120-124);
    LiteralText(literal) at :122 cannot throw on parsed input: Lexer.scala:95
    refuses a quoted character outside literalChars and TermParser.scala:39,
    :109 builds every other Const from digits, "nil" or a decimal;
    SymbolText.of at :84 accepts the infix relation names (TokenText.scala)
  4 convention conformance — 1 comment defect fixed (FragmentCheck.scala:32);
    if/else check PASS — FragmentCheck.scala:42 is one comparison
  5 test presence — PASS after two tests added (FragmentCheckSpec.scala:317,
    :337); sbt test pass, JVM and Node
  6 drive-by changes — PASS — 808c3fd edits docs/ADR-004.md:69 and
    docs/PLAN-catalog-errors-in-vql-error.md, both under that plan, whose
    inventory lists docs/ADR-004.md
  7 boyscout pass — 3 findings reported (README.md:70, README.md:10,
    docs/ADR-018.md:248); 1 stale comment fixed (FragmentCheck.scala:15-16)
  8 terminology freshness (name level) — discharged: no glossary
escalated:
  core/src/main/scala/vql/fragment/Fragment.scala:19, :42 — a public case gains a
    parameter and a public enum gains a case, which register matches
    (wire contracts: public API shapes) → complex
  core/src/main/scala/vql/fragment/FragmentCheck.scala:81-89, :122 — a walk over a
    parser's output whose results carry caller text as SymbolText and
    LiteralText (security trigger: the fields of a refusal that reports on a
    caller's text) → security
```

```
COVERAGE  tier=complex  scope=the routine tier's escalation to complex:
          core/src/main/scala/vql/fragment/Fragment.scala:19, :42 (File scope
          source 2, the open changes ba1ae06 and aca8d96, now pushed)
read-in-full:
  core/src/main/scala/vql/fragment/Fragment.scala
  core/src/main/scala/vql/fragment/FragmentCheck.scala
  core/src/test/scala/vql/fragment/FragmentCheckSpec.scala
  core/.jvm/src/test/scala/parser/NestingBoundStackSpec.scala
  core/src/main/scala/parser/FOLAtomParser.scala
  core/src/main/scala/parser/TermParser.scala:1-140
  core/src/main/scala/lexer/TokenText.scala
  core/src/main/scala/vql/id/Identifiers.scala
  docs/PLAN-targeting-formula-inspection.md
  docs/ADR-018.md
  docs/ADR-024.md:40-75
  CHANGELOG.md:1-40
  register: modules/common/src/main/scala/com/risquanter/register/domain/data/TargetingPredicate.scala
  register: modules/common/src/test/scala/com/risquanter/register/domain/data/TargetingPredicateSpec.scala
  register: build.sbt:1-20, :40, :105
discharged:
  1 adversarial line scan — PASS — FragmentCheck.scala:83, :89: predicate
    names reach lowerCase only from FOLAtomParser.scala:40 (relOps) and
    :66-82 (Token.Word), and StringUtil.scala:74-75 makes every Word ASCII,
    so Character.toLowerCase gives the same result on the JVM and Scala.js
    for every parsed name; SymbolText.of at :84 admits all five relOps
  2 removed-behaviour audit — PASS — the old early return at a quantifier and
    at a refused Fn is replaced by a prepend (FragmentCheck.scala:78-79, :105),
    so the head of violations equals the old checkTargeting result whenever
    forbiddenPredicates is empty; pinned by FragmentCheckSpec "check returns
    the first element of violations" (hand-built and parsed lists)
  3 cross-module tracing — PASS, 1 finding reported — engine callers of
    FragmentCheck: NestingBoundStackSpec.scala only; register callers:
    TargetingPredicate.scala:146 (one Targeting construction) and :149-153
    (one match over FragmentViolation); both fail to compile on 0.23.0, the
    match through register build.sbt:12 "-Wconf:msg=match may not be
    exhaustive:error"; no caller relies on check stopping early
  4 state and interleaving — discharged: the library holds no shared mutable
    state
  5 invariant reconstruction — PASS — check == violations.headOption.toLeft(())
    by construction at FragmentCheck.scala:21-24; recursion depth of
    termViolations through refused applications measured at 256 left-nested
    and 256 right-nested `-` and 64 parentheses on a 1 MiB JVM thread
    (NestingBoundStackSpec.scala, new test) and on Node (FragmentCheckSpec.scala,
    new test)
  6 design altitude and if/else — PASS — FragmentCheck.scala:42 is one
    comparison
  7 terminology freshness (meaning level) — discharged: no glossary
  8 inline verification — 1 finding kept (NOTE), the stack-depth candidate
    refuted by the new tests
  suite: sbt test pass, JVM and Node
escalated:
  none
```
