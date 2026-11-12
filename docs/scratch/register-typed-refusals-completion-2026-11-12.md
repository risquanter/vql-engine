# vql-engine 0.18.0 is done — completion handover for register

**Date:** 2026-11-12
**For:** the register session working steps 5.6 and 5.7 of
`docs/dev/plans/PLAN-UES-UNIFIED-ERROR-SHAPE.md` in the repository `register`.
**Read with:** [register-typed-refusals-2026-11-12.md](register-typed-refusals-2026-11-12.md),
which lists every engine case, its fields and the register sites that change.
This note does not repeat that list. It states what shipped, and where the
implementation differs from the plan register read.

---

## 1. What shipped

`docs/DONE_PLAN-typed-refusals.md` is implemented in full. All six phases
landed, and `sbt test` is green on the JVM and on Scala.js.

| Item | State |
|---|---|
| Version | `0.18.0` in `build.sbt`, a breaking pre-1.0 minor bump from `0.17.0` |
| Tag | `v0.18.0`, SSH-signed, on the commit the ci-build run built |
| GitHub release | created by `release.yml` on that tag |
| Maven Central | staged by `release.yml`; check that `com.risquanter::vql-engine:0.18.0` resolves before pinning it |
| Changelog | `CHANGELOG.md`, section `0.18.0` |
| New records | `docs/ADR-022.md` (typed refusals) and `docs/ADR-023.md` (errors declared once, `vql.id`), both `Accepted` |
| Superseded records | `docs/ADR-019.md` and `docs/ADR-021.md`, both `Superseded by ADR-023` |

What register does is unchanged from the API handoff: pin `0.18.0`, then
change the sites its §6 names.

---

## 2. Where the implementation differs from the plan

Each difference below was ruled by the user during the work. The plan document
records each ruling where it sits.

### 2.1 `vql.typed.TypeId` is a type alias, not an `export`

The plan specified `export vql.id.{SymbolName, TypeId}` in
`vql/typed/TypeDefs.scala`. Through an exported companion, `SymbolName(s)` was
inferred as `String`, and `examples/VagueDemo.scala` stopped compiling. The
shipped form is an alias with a forwarding `apply`:

```scala
type TypeId = vql.id.TypeId
object TypeId:
  def apply(value: String): TypeId = vql.id.TypeId(value)
```

`SymbolName` has the same form. **Effect on register:** none to write. Every
import of `vql.typed.TypeId` and `vql.typed.SymbolName` still resolves, and the
alias is the same type as `vql.id.TypeId`, so a value from either path fits
the other.

### 2.2 A tree name is checked per position

The plan had one check, `SymbolText.admits`, for every name in a tree. It
admitted punctuation as a name and did not tell a predicate from a function.
The shipped code has one check per position: a predicate name, a function
name, a variable name and a constant name each admit only what the parser can
produce there. **Effect on register:** none. Register builds no tree. The
content types in the API handoff's table are unchanged.

### 2.3 `CLAUDE.md` was edited by the implementing session

The plan said the session presents three replacements for the repository's
`CLAUDE.md` and the user applies them. The user told the session to apply
them, and it did. **Effect on register:** none.

---

## 3. What register's step 5.7 is expected to write

vql-engine's routing skill, `.claude/skills/vql-adr-constraints/SKILL.md`,
gained a Layer 4 row. It names a section that does not exist yet: the section
of register's ADR-035 on what of the caller's query text a refusal may carry.
Step 5.7 writes that section. Once it exists, the row resolves.

The section rests on three facts from ADR-022:

- A token in a refusal carries text only for a word, a decimal or a quoted
  literal. An unknown operator run and a stray character carry no text.
- A quoted literal's content is `[A-Za-z0-9 /_.+-]*`. The apostrophe is not in
  it. A word, which is unquoted text, is `[A-Za-z0-9_']+`.
- `UnboundVariableError.boundVariables` is the one refusal field that holds
  caller text as a `String`. It includes the keys of register's answer tuple.

---

## 4. Not part of this plan

The release procedure was rewritten in the same session. `docs/RELEASE.md`
describes the signed-tag release in six steps. The sibling repositories
`hdr-rng` and `metalog-distribution` received the same `release.yml` checks
and a `docs/RELEASE.md` of their own. None of this changes anything register
consumes.
