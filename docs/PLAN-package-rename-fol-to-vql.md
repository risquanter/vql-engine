# PLAN — Scala-package rename `fol.*` → `vql.*` (T-000, 0.13.0)

**Status:** Phase 1 (rename) and Phase 2 (live-docs sweep, version, CHANGELOG,
TODOS) done; full suite green both platforms (795 each). Remaining: the user's
commit + `0.13.0` publish, register's lockstep import rewrite (Phase 3 handoff).
**Workstream:** rename the vague-layer Scala packages from `fol.*` to `vql.*`.
**Tracks:** [TODOS.md T-000](TODOS.md#t-000--scala-package-rename-fol--vql).
Ships as the breaking Central release **0.13.0**, sequenced after the
fragment-membership API (0.12.0).
**ADR:** none new — this is a mechanical rename with no design decision. It
updates the package paths shown in existing ADRs (ADR-004 diagram and others in
§6), it does not add or supersede an ADR.

---

## 1. Objective

Rename every vague-layer package from the `fol.*` prefix to `vql.*`, so the
package namespace matches the published artifact name (`vql-engine`) and the
project name. The artifact rename (`fol-engine` → `vql-engine`) already shipped;
this closes the remaining mismatch — the code still declares `package fol.*`.

The change is a pure rename. No type, signature, or behaviour changes. After it,
`import fol.semantics.VagueSemantics` becomes `import vql.semantics.VagueSemantics`,
and the full test suite stays green on both platforms with no test-logic edits
(only the tests' own `package`/`import` lines move).

## 2. Scope — exactly the nine `fol.*` packages

Rename all nine, the complete set (`core/src/main` and `core/src/test`):

| From | To |
|---|---|
| `fol.error` | `vql.error` |
| `fol.fragment` | `vql.fragment` |
| `fol.logic` | `vql.logic` |
| `fol.parser` | `vql.parser` |
| `fol.quantifier` | `vql.quantifier` |
| `fol.result` | `vql.result` |
| `fol.sampling` | `vql.sampling` |
| `fol.semantics` | `vql.semantics` |
| `fol.typed` | `vql.typed` |

`fol.fragment` is in scope: T-011 has landed (shipped in 0.12.0), so the
"once T-011 lands" condition in the T-000 scope note is satisfied.

### What must NOT change

This rename touches the package path segment `fol` only. The following are
different tokens that happen to contain the letters and MUST be left exactly
as they are:

1. **The foundation top-level packages** — `logic`, `parser`, `semantics`,
   `printer`, `lexer`, `util`, and `examples`. These have no `fol.` prefix and
   are not renamed (ADR-004: the FOL foundation is a separate layer the vague
   layer imports). Note the collision-free result: foundation `parser` /
   `semantics` / `logic` stay as-is while vague `fol.parser` / `fol.semantics` /
   `fol.logic` become `vql.parser` / `vql.semantics` / `vql.logic` — still
   distinct, no new clash.
2. **The uppercase `FOL` concept and identifiers** — the `FOL` enum, `Formula[FOL]`,
   `FOLParser`, `FOLSemantics`, `FOLPrinter`, `FOLAtomParser`, `FOLUtil`, and every
   other `FOL`-containing name. `FOL` means first-order logic; it is not the
   package.
3. **`com.risquanter`** (build organization) and **`vql-engine`** (artifact
   name) — unrelated to the Scala package path; already correct.

### Collateral hazards — lowercase `fol.` strings that are not package refs

A blind text replace of `fol.` would corrupt these. The rename must never touch
the bare `fol.` string; it operates only on the nine qualified prefixes in §2.

- **`fol.ml`** — scaladoc citations of Harrison's OCaml source file `fol.ml`
  (`fol/quantifier/Quantifier.scala:87,97,159`, `util/StringUtil.scala:91`).
  These are references to the ported source, not packages. Leave them.
- **`fol.predicate`** — a local val named `fol` bound by `case Atom(fol)` in
  tests (`fol/logic/ParsedQuerySpec.scala:184,227`,
  `fol/parser/VagueQueryParserSpec.scala:47,284,297,311`). `fol` is a pattern
  variable; `.predicate` is a field access. Leave them.

## 3. Mechanical method (safe, prefix-scoped)

Do not run `sed s/fol\./vql./`. Operate on the nine qualified prefixes only.

1. **Move the directory tree.** `git mv core/src/main/scala/fol core/src/main/scala/vql`
   and `git mv core/src/test/scala/fol core/src/test/scala/vql`. This relocates
   every source file so the on-disk path matches the new package. (`git mv`
   preserves history for the review.)
2. **Rewrite `package` declarations.** `package fol.<pkg>` → `package vql.<pkg>`
   for the nine packages. There are no bare `package fol` declarations (every
   declaration is `package fol.<sub>`), so a `package fol.` → `package vql.`
   replacement is exact and safe.
3. **Rewrite `import` statements.** `import fol.<pkg>` → `import vql.<pkg>` for
   the nine prefixes. 18 files import `fol.*` today. Do this per-prefix
   (`import fol.error` → `import vql.error`, etc.) so the bare-`fol.` hazards in
   §2 are never matched.
4. **Rewrite scaladoc/comment cross-references** that name a package, e.g.
   `fol.typed.TypedSemantics` in a doc comment → `vql.typed.TypedSemantics`.
   Same per-prefix rule; `fol.ml` and `fol.predicate` are excluded by it because
   neither is one of the nine prefixes.

**Verification after the edits:** `grep -rnoP "\bfol\.(error|fragment|logic|parser|quantifier|result|sampling|semantics|typed)\b" core/src`
must return nothing. `grep -rn "fol\.ml\|case Atom(fol)" core/src` must be
unchanged from before. Then compile.

## 4. Phases

### Phase 1 — Rename + green suite (both platforms)
- Steps 1–4 of §3 across `core/src/main` and `core/src/test`.
- `sbt folEngineJVM/compile folEngineJS/compile` clean (0 warnings), then the
  full `sbt test` — both `folEngineJVM` and `folEngineJS` green. (The sbt
  project ids `folEngineJVM` / `folEngineJS` are build-definition names, not
  Scala packages; they are out of this rename's scope and unchanged.)
- HARD STOP for review before docs/version.

### Phase 2 — Live-docs sweep, version, CHANGELOG
- **Version:** `build.sbt` `0.12.0` → `0.13.0`; `README.md` install snippet and
  `docs/Architecture.md:161` publish snippet bumped to match. The user runs the
  publish; HEAD tracks the version being shipped.
- **Live docs** (describe current state — must move to `vql.*`):
  `README.md`, `docs/Architecture.md` (package-tree map at line 19: `fol/` →
  `vql/`, plus any `fol.` prose), `docs/VagueQuantifiers.md`, and the accepted
  ADRs that show package paths: **ADR-001, ADR-002, ADR-003, ADR-004** (the
  two-layer diagram), **ADR-006, ADR-018**. Update every `fol.<pkg>` reference in
  these to `vql.<pkg>`.
- **CHANGELOG.md:** new `0.13.0` entry — breaking: "All `fol.*` packages renamed
  to `vql.*`. Downstream imports rewrite mechanically (`import fol.X` →
  `import vql.X`); no API shape changes."
- **TODOS.md:** T-000 status → DONE; note the shipped release.

### Phase 3 — register handoff note
- No engine code. Record in `docs/scratch/` (or append to the existing register
  handoff note) that 0.13.0 requires register's foladapter module to rewrite its
  whole `import fol.*` surface to `import vql.*` in lockstep with the pin bump —
  `RiskTreeKnowledgeBase`, `QueryServiceLive`, `QueryRequest`, `AppError`, and
  the foladapter specs (per TODOS.md T-000). register consumes the engine only as
  a Central binary, so it adopts this as a published-release pin bump; the engine
  side does not edit register.

### Historical docs — deliberately NOT rewritten
`DONE_PLAN-*.md`, `CLOSED_*.md`, and `docs/scratch/*` dated review records are
point-in-time records and keep their `fol.*` text (the "docs as current state"
rule excepts dated/historical records; rewriting them would falsify what was true
when written). This plan document is the one exception among live docs — it
describes the rename itself, so its `fol.*`/`vql.*` mentions are the subject
matter, not stale references.

## 5. Validation checklist

- **No collateral:** the two §3 verification greps pass (`fol.ml` and
  `case Atom(fol)` untouched; zero surviving `fol.<pkg>` qualified refs in code).
- **Foundation untouched:** `git diff` shows no `package`/`import` change to
  `logic`, `parser`, `semantics`, `printer`, `lexer`, `util`, `examples`.
- **`FOL` identifiers untouched:** no diff hunk renames `FOL`, `FOLParser`,
  `FOLSemantics`, `FOLPrinter`, `FOLAtomParser`, `FOLUtil`.
- **Full suite green both platforms** (`sbt test` → both `folEngineJVM` and
  `folEngineJS` pass). Compiling is not running; the suite must run.
- **Cross-build:** the rename is platform-agnostic; JVM and JS recompile from the
  same relocated sources.

## 6. ADR compliance

- **ADR-004 (two-layer architecture):** the vague/foundation split is unchanged;
  only the vague layer's package label changes. ADR-004's package diagram is
  updated to `vql.*` in Phase 2 (it currently shows `fol/`).
- **ADR-007 (frozen Harrison parser core):** untouched — the foundation `parser`
  package is not renamed, and `fol.ml` citations are preserved. No characteristic
  C1–C13 affected.
- **No new design decision:** a rename introduces no new ADR. Existing ADRs are
  refreshed to current package names as part of the live-docs sweep.

## 7. Risk assessment

Low. The only failure mode is collateral text replacement (§2 hazards), fully
guarded by the prefix-scoped method (§3) and the verification greps (§5). The
compiler catches any missed `import`; the full suite catches any missed
reference. There is no behavioural surface to regress.

## 8. register handoff (downstream, separate repo)

register is the sole downstream consumer and reaches the engine only through
published Central binaries. It adopts 0.13.0 as a pin bump plus a mechanical
`import fol.*` → `import vql.*` rewrite across its foladapter module. The engine
repository does not edit register; the handoff is the CHANGELOG note plus the
Phase 3 record. `../register` remains read-only from this workstream.

## 9. Preconditions (sequencing gate)

1. **0.12.0 must be published to Central first.** T-000 is sequenced after the
   0.12.0 fragment API so register absorbs one bounded breaking change per pin
   bump (TODOS.md "Release sequencing — T-011 and T-000"). As of this plan,
   0.12.0 is committed locally but unpushed (no release tag), so this gate is
   open. The user runs the push and release.
2. **Working tree clean on `main`** at the start of Phase 1, so the rename diff
   is reviewable in isolation.
