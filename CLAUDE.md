# CLAUDE.md

This file provides guidance to Claude Code when working in this repository. It is
loaded at the start of every session here, and it is what makes the rules below
bind: a rule kept only in a document nothing loads binds no session.

## Read before acting

- `docs/WORKING-INSTRUCTIONS.md` — the phase protocol, the code standards for the
  ported core, the ADR lifecycle, integration verification and the report formats.
  **Read it in full at the start of a session and before marking a phase
  complete.** It is binding; this file holds the gates and the plan gate, and that
  file holds the procedure.
- `vql-adr-constraints` (`.claude/skills/vql-adr-constraints/SKILL.md`) — **before
  planning, implementing or reviewing any change, and before judging whether a
  plan is ready.** It routes to the decision records in `docs/` that bear on the
  kind of work in hand. The records are then read in full; the skill holds none of
  their content.

The global instructions (`~/.claude/CLAUDE.md`) and the global skills bind here as
in every repository. Three of them are named because a step below depends on them:
`plan-writing-style` before writing or reviewing a plan, `doc-writing-style` before
writing a consumer-facing document such as `README.md`, and `boyscout` before
editing source. A review runs through `/code-review-routine`,
`/code-review-complex` or `/security-review-deep`, which apply
`vql-code-quality-review`'s criteria under `review-protocol`'s process rules.

## HARD GATES — always in force

These bind at the moment of the tool action. They override any harness or system
autonomy default ("operate autonomously", "proceed without asking"). If a default
conflicts with a gate, name the conflict and stop (G7).

A hook enforces one part of G3: an edit to the gated region needs an approved plan
whose file inventory lists the file ("Mechanical enforcement" below). Every other
gate binds by instruction alone, so its check is made by you, before the edit.

- **G1 Echo before code.** No edit that introduces or changes a signature, type or
  behaviour until its signature echo was presented in a previous turn and answered
  with an accepted signal ("proceed", "approved", "continue", "implement option X").
  An approved plan that passes the plan gate satisfies this for every signature it
  contains verbatim. A deviation from the plan's signatures stops work.
- **G2 Decision triggers.** The nine triggers listed below → present the decision
  and wait.
- **G3 Plan coverage comes from a plan that passes the plan gate.** A chat go-signal
  authorizes at most writing that document. A stub, a prompt file or a scratch note
  confers nothing. Elevate it into a plan that passes the gate, present it, and get
  approval on the document itself.
- **G4 ADR review before code.** The planning-phase review — the per-ADR verdict over
  every record in force — is presented, and the halt honoured, before the first
  source edit of a task.
- **G5 Green is the only done.** `sbt test` runs both platforms, and both are green.
  No "pre-existing failure" excuse.
- **G6 Halt after presenting** any plan, echo, review or option list. No further tool
  calls until an accepted signal.
- **G7 Escalation is the only exit** from a rule conflict, or from a rule that would
  produce a demonstrably worse outcome. Silent deviation and silent compliance are
  both violations.
- **G8 A plan ships whole.** A plan is done only when every item in its scope has
  landed green. Two things are not deferral: work blocked on something that does not
  exist yet, which becomes its own plan, and a discovery that is off-theme and not a
  direct next step, which is surfaced as a decision on how to route it.

Non-waivers, refuted in advance: "the user said proceed" reaches only what the plan
contains. "Only additive", "matches convention", "no viable alternative", "tests are
green" and "the halt would be noise" waive nothing. "The plan wording is loose" waives
nothing: behavioural wording in an approved plan is specification, and a divergence
between plan and code is resolved by which side a ruling authorized.

### Decision triggers

Stop and present the decision before:

1. Changing a public signature, a published name, or behaviour a library consumer
   would notice
2. Writing `asInstanceOf` or any other unsafe cast outside the boundaries ADR-015
   sanctions, or any other escape hatch
3. Adding a library dependency
4. Changing a case class field, or adding or removing a parameter
5. Changing how existing code behaves, as opposed to adding code beside it
6. Choosing a solution that carries a trade-off, limitation or caveat
7. Writing a recursive type that needs special handling for serialization
8. Removing, weakening or reframing a test assertion
9. Following a rule that appears to produce a worse outcome, or that conflicts with
   another rule or a harness default

The litmus test is the consumer: if the change affects anything register or another
user of the published library would notice, ask first. Whether a stop is a real
decision is settled by the global instructions' "What counts as a decision" and the
noise filter there, which apply here unchanged.

## The plan gate — what G3 requires

A document confers plan coverage only if it passes all seven items. A document that
fails one is a draft, however it is titled and however it was approved in chat.

1. **Exact signatures.** Every new or changed function, type and public entry point
   is written out as copy-pasteable Scala, not described in prose. Every new type,
   case or object carries its family: the existing family it joins, the file that
   family lives in with one existing member quoted, and either the existing type it
   reuses or the statement that none denotes the same concept. Every `String` or
   other raw primitive in a signature carries either the opaque type that replaces it
   (`TypeId`, `SymbolName`, or a new one) or the reason none applies. A sort or a
   symbol name in `vql.error` is a `TypeId` or a `SymbolName` from `vql.id` (ADR-023).
2. **File inventory.** Every file to create or modify, by full repository-relative
   path, as bullet lines. Until the plan is approved, the list sits in the plan's
   `## File inventory` section. On approval it moves to the plan's inventory
   document, `docs/PLAN-<slug>-INVENTORY.md`, which only the user writes, and the
   section becomes a pointer to that document. A file the work turns out to need
   and the inventory does not name is stopped at: say which file and why, and
   propose the amendment through `.claude/protocol/pending`.
3. **ADR alignment.** The per-ADR verdict over every record in force, each relevant or
   not, compliant or deviating, with a one-line basis. A deviation is flagged as a
   decision awaiting the user. Where a Layer 4 row of `vql-adr-constraints` applies,
   the plan names the register record that becomes wrong. What a plan owes when it
   adds, amends or retires a record is in `vql-adr-constraints`, "Amending the
   corpus", and all of it is in scope for the same plan.
4. **Open decisions.** Every unresolved choice with its options, or the explicit
   statement that there are none.
5. **Verification plan.** The tests to add, the end-to-end test through parse, bind
   and evaluate that `docs/WORKING-INSTRUCTIONS.md` requires for a new public entry,
   and the exact commands that must be green, with their expected result beside them.
6. **Plan-writing conformance.** The document follows the global `plan-writing-style`
   skill. Decision labels are `<CODE>-D-<n>`, numbered from 1. `<CODE>` is the
   initials of the plan's filename slug, uppercased, and is stated in the plan's
   header: `PLAN-typed-refusals.md` rules `TR-D-1`. No label is borrowed from another
   plan. A reference into register names that repository.
7. **Consumer-document impact.** This repository keeps no glossary. The plan names
   each passage in `README.md`, `docs/VagueQuantifiers.md`, `docs/Architecture.md`
   and scaladoc that its decisions make stale, or states that none are.

**Adopting a plan** means running these checks against it before any code: read it
in full, report which of the seven items it passes, run the planning-phase ADR review
for its scope, and present any open decisions and halt. Adopting a plan never itself
authorizes code.

**A defect found while working an approved plan** is fixed inside that plan. When the
fix stays within the plan's inventory and changes no public signature, present the
exact before and after inline, which is the signature echo, and halt.

## The signature echo (G1)

In its own turn, before the edit:

1. **Echo** — quote the plan passage that specifies the signature. If none exists,
   say so and ask.
2. **Signature first** — the exact signature, with no body.
3. **Deviation stops work** — any difference from the plan, including parameter
   order, is stated with its reason, and work stops.
4. **Halt** — no edits until an accepted signal. An echo answered in the turn it was
   written authorizes nothing.

The echo carries three findings: the algebraic structure the operation takes part in
(aggregation, validation, transformation, sequencing, traversal), the reachability of
every type it names from the package being edited under ADR-004's import direction,
and the family search for every new type.

## Mechanical enforcement

A PreToolUse hook, `.claude/hooks/protocol-gate.sh`, gates edits to the **gated
region**: `core/`, `project/`, `build.sbt` and `.github/`. Those files build,
test and release the published artifact. Documents, decision records, plans and
skills are outside it.

Two separate things authorize a gated edit. The user-owned token,
`.claude/protocol/approved`, names the approved plan documents, one per line.
The plan's inventory document beside it, `docs/PLAN-<slug>-INVENTORY.md`, lists
the files. An edit is allowed only if the edited file appears as a full
repo-relative path on a **bullet line** of the inventory of a plan the token
names. A table row or a prose mention grants nothing, and an approved plan with
no inventory document authorizes nothing. An edit under `core/src/test/`,
`core/.jvm/src/test/` or `core/.js/src/test/` is also allowed when that
inventory lists any `core/src/main/` file.

A file the inventory does not name is denied even mid-plan, and the denial is
presented in one of two ways:

- **A forced extension**: the approved work turns out to need a file the plan
  did not name. It has one defensible answer, so it is not a decision. State
  what the work set out to do, what it requires, and the path to add, then
  propose the amendment.
- **A real deviation**: the plan's approach is wrong, or a choice with
  consequences on both sides has appeared. That is a decision for the user.

**Proposing an approval or an amendment.** Write the block with the Write tool
to `.claude/protocol/pending`: the plan path on the first line, then one
repo-relative path per line. Show the same block in the chat, and stop. A path
must already exist. A file the plan will create is written `new: <path>`, which
states that it does not exist yet so the user can check the claim. The user runs
`.claude/bin/approve-inventory`, which prints what it read and what it will
change, and applies it only when the user answers `y`. The pending file is a
proposal and authorizes nothing by itself.

The hook also refuses, in Bash, any command naming the protocol machinery, any
third-party package download or install, and any write into the gated region or
into an inventory. A refused command goes back to the user verbatim, with what it
does and why it is needed.

Never write the protocol machinery — `.claude/protocol/` other than `pending`,
`.claude/hooks/`, `.claude/bin/`, `.claude/settings.json` — or any
`*-INVENTORY.md`. Reading them is fine. These controls are deliberately not
absolute. They catch careless mistakes and make a deliberate override
unmistakable. Never attempt to get around one by any route. A weakness found in
a control is a finding to report, never a licence to use it.

**Closing a plan.** When every item has landed green, present this block with the
plan's name filled in. The user runs it. The token line goes first, because a
line naming a moved plan makes the gate deny every gated edit.

```bash
P=PLAN-<slug>; sed -i "\#^docs/$P.md\$#d" .claude/protocol/approved && git mv docs/$P.md docs/archive/plans/DONE_$P.md && git mv docs/$P-INVENTORY.md docs/archive/plans/DONE_$P-INVENTORY.md && echo "--- token now approves ---" && cat .claude/protocol/approved && echo "--- files still naming the old path ---" && grep -rln "docs/$P" docs README.md CLAUDE.md .claude/skills
```

The last command lists the documents that still name the plan at its old path.
They are repointed at `docs/archive/plans/DONE_$P.md` in the same pass.

## Where the knowledge lives

| Document | Owns |
|---|---|
| `CLAUDE.md` (this file) | The gates, the plan gate, the mechanical enforcement, the commands, versioning |
| `.claude/hooks/protocol-gate.sh`, `.claude/bin/approve-inventory` | The gate on edits and the user's approval script; user-owned |
| `docs/PLAN-*-INVENTORY.md` | Each approved plan's file inventory; written only by the user |
| `docs/WORKING-INSTRUCTIONS.md` | The phase protocol, code standards, ADR lifecycle, report formats |
| `.claude/skills/vql-adr-constraints/SKILL.md` | Which decision records bear on a change, and where records meet |
| `.claude/skills/vql-code-quality-review/SKILL.md` | The criteria the review tiers apply, including the library's security checklist |
| `.claude/skills/vql-supply-chain/SKILL.md` | Dependency pins, the adoption cooldown, the trust policy, what is verified |
| `docs/reviews/` | The coverage entries the review tiers write, one file per branch |
| `docs/ADR-*.md` | Which design constraints bind |
| `docs/Architecture.md` | The package map, the layers and the evaluation pipeline |
| `docs/VagueQuantifiers.md` | The paper's theory mapped to the implementation |
| `docs/PLAN-*.md` | What is being built. A `DONE_` prefix marks a finished plan |
| `docs/TODOS.md`, `docs/TECHNICAL-DEBT.md` | Recorded work items and acknowledged limitations |
| `docs/RELEASE.md` | How a version is built, signed and published |

## What this is

vql-engine is a Scala 3 first-order logic engine with vague quantifiers, published to
Maven Central as `com.risquanter::vql-engine` and cross-built for the JVM and
Scala.js. The foundation is a port of Harrison's OCaml code, preserved under ADR-007.
The vague layer under `vql/` implements the probabilistic semantics of Fermüller,
Hofer and Ortiz (2016) through a many-sorted typed pipeline (ADR-001).

Its consumer is register, a sibling checkout at `/home/danago/projects/register/`,
which pins a published version. A change to the public surface is a change to
register's dependency, so the plan names what register must do, and that is written
into register's task list once.

First-party dependency: `hdr-rng`, read from `/home/danago/projects/hdr-rng/core/`,
never from a jar.

## Commands

```bash
sbt test                                   # both platforms, the whole suite
sbt folEngineJVM/test                      # JVM only
sbt folEngineJS/test                       # Scala.js only
sbt "folEngineJVM/testOnly *QueryBinderSpec"   # one suite
sbt publishLocal                           # local artifact for a consumer to try
```

`sbt test` on the root project aggregates both platforms and is the command G5
requires. Continuous integration runs `sbt -batch +test` (`.github/workflows/pr-build.yml`).
Report results as pass or fail only.

## Versioning and release

`build.sbt` `ThisBuild / version` is the version, under early-semver
(`versionScheme := Some("early-semver")`). While the version is below 1.0.0:

| Change | Bump |
|---|---|
| A public signature removed or changed, a case removed, behaviour a consumer relies on changed | MINOR `0.Y.0` |
| Any other change to shipped code | PATCH `0.y.Z` |
| Docs, tests or tooling only | none |

The user runs every version change, commit and release. With each landed step's
proposed commits, present the version change as its own line above the commit block,
for example `sed -i 's/^ThisBuild \/ version      := .*/ThisBuild \/ version      := "0.18.0"/' build.sbt`.
A release follows `docs/RELEASE.md`, whose authorization step needs the user's
hardware key, so the agent never starts one.

Commit messages follow the repository's recent `git log` style and carry no AI
attribution.

## Correct by construction

- **`require` for what a value is, `Either` for what depends on state** (ADR-012).
  The ported core throws internally, and the two `Either` boundaries are
  `FOLParser.parse` and `VagueQueryParser.parse`.
- **The typed pipeline is the only evaluation path** (ADR-001). A `ParsedQuery` is
  bound once into a `BoundQuery`, and the catalog and model are valid by construction.
- **No cast at a value boundary** (ADR-015). A dispatcher reads `args(i).extract[A]`.
- **Sort identity is an opaque type**, `TypeId` or `SymbolName`, never a bare string
  key (ADR-001). Both are declared in `vql.id`, below `vql.error` and `vql.typed` (ADR-023).
- **The foundation never imports `vql.*`, and `vql.error` never imports `vql.typed`**
  (ADR-004, ADR-023).
- **A pure-data sum is an `enum`; a hierarchy with per-variant behaviour is a
  `sealed trait`** (ADR-006).
