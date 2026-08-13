# register handoff — `fol.*` → `vql.*` package rename (engine 0.13.0)

Engine release **0.13.0** ([T-000](../TODOS.md#t-000--scala-package-rename-fol--vql),
[PLAN-package-rename-fol-to-vql.md](../PLAN-package-rename-fol-to-vql.md)) renames
every vague-layer Scala package from `fol.*` to `vql.*`. register consumes the
engine only as a Central binary, so it adopts this as a pin bump (`0.12.x` →
`0.13.0`) plus a mechanical import rewrite.

## What register does

Rewrite every `import fol.<pkg>` → `import vql.<pkg>` across the foladapter
module. No API shape, type, or behaviour changed — this is a pure namespace move,
so the rewrite is find/replace on the import lines with no call-site logic edits.

The nine renamed packages: `error, fragment, logic, parser, quantifier, result,
sampling, semantics, typed`.

## Files register updates (per T-000 scope)

- `RiskTreeKnowledgeBase`
- `QueryServiceLive`
- `QueryRequest`
- `AppError`
- the foladapter specs

Exact import lines are whatever `grep -rn "import fol\." foladapter` returns in
register; each becomes `import vql.` for the same package.

## What does NOT change

- The FOL foundation packages (`logic`, `parser`, `semantics`, `printer`,
  `lexer`) have no `fol.` prefix and are unchanged.
- Every `FOL` identifier (`FOL`, `FOLParser`, `FOLSemantics`, `Formula[FOL]`) is
  unchanged — the rename is the lowercase package segment only.
- The artifact coordinates: still `com.risquanter %%% vql-engine`, version
  `0.13.0`.

## Sequencing

0.13.0 is the isolated breaking change after the 0.12.x fragment-membership API,
so register absorbs one bounded change per pin bump: the import rewrite here, with
no new API to integrate in the same diff.
