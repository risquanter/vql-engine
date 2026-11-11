# Working Instructions

This document defines the working protocol for all changes in this repository.
`CLAUDE.md` at the repository root holds the gates and the plan gate and points
here; this document holds the procedure. Which decision records bear on a change
is decided by the `vql-adr-constraints` skill
(`.claude/skills/vql-adr-constraints/SKILL.md`).

---

## Governance

### Progress Control

- **User is in charge** of progress approval, review, and decisions
- Each phase requires **explicit approval** before proceeding to the next
- Agent will **ask questions** when facing ambiguity rather than making assumptions
- Agent will **not proceed** to the next phase without user's "proceed" confirmation

### Decision Protocol

1. Agent presents options or proposed approach
2. User reviews and either approves, requests changes, or asks questions
3. Agent implements only after approval
4. Agent presents results for review before marking phase complete

---

## Code Standards

### Scala

- Code must be **idiomatic Scala 3** (except the OCaml-ported foundation
  layer, which ADR-007 preserves in its ported style)
- Follow existing codebase conventions (check existing files for style)
- Construction invariants via `require`, state-dependent failures via
  `Either[QueryError, A]` (per ADR-012)

### OCaml-Ported Core (ADR-007)

- Files in ADR-007's scope tables (Tiers 1–3) keep their ported style:
  exception backtracking, tuple-threaded parsers, list-as-set operations
- Any change to those files is benchmarked against characteristics C1–C13;
  breaking a characteristic requires explicit justification and user approval
- Verbatim OCaml scaladoc comments are the traceability link to Harrison's
  textbook — never delete or paraphrase them

---

## ADR Compliance

### Mandatory Review Process

**ALL proposed code changes MUST be reviewed against existing ADRs** at two critical points:

#### 1. Planning Phase (Before Implementation)

Before writing any code, agent must:

1. **Review all accepted ADRs** to understand current architecture
2. **Record an explicit per-ADR verdict for every active ADR** — relevant or
   not, with a one-line basis. Listing only the ADRs judged "materially
   touched" is not sufficient. This is **mandatory** whenever the change is a
   cross-cutting concern (typing, layering, error-channel, encoding, or value
   boundaries) where topical overlaps are non-trivial: a change that looks
   local to one ADR routinely stales code sketches or implementation rows in
   others (e.g. an error-rendering rename touching ADR-014/ADR-017 impl tables).
   The verdict table is what forces those out into the open.
3. **Identify potential conflicts** with proposed changes
4. **Document alignment or deviations** in planning proposal
5. **Notify user immediately** if any deviation is detected, in the global
   `decision-guide` format: the affected ADR and what it states, the
   deviation, what you plan to do, and the options — at least changing the
   proposal to comply and changing the ADR — each with a concrete example and
   its pros and cons, and your recommendation labelled as yours
6. **Wait for user decision** before proceeding

#### 2. Review Phase (After Implementation)

After implementing changes, agent must:

1. **Re-validate all code** against accepted ADRs
2. **Check for unintended deviations** introduced during implementation
3. **Document compliance** in completion report
4. **Notify user immediately** if any deviation is found, in the global
   `decision-guide` format: the affected ADR and what it requires, the code
   location as `path/to/file.scala:line`, what was done, and the options — at
   least refactoring the code to comply and changing the ADR — each with a
   concrete example and its pros and cons, and your recommendation labelled as
   yours
5. **Wait for user decision** before marking phase complete

### Validation Requirements

At each phase, validate implementation against every record in force. The set,
each record's status and what it governs are Layer 2 of the
`vql-adr-constraints` skill, which is the one list kept. An `Accepted` record is
validated against. A `Proposed` record is validated against once it is
accepted. ADR-00X is the meta template governing ADR structure. The records
under `docs/archive/` covered the retired untyped backend and are not validated
against.

### ADR Lifecycle

```
Proposal → Implementation → Review → Accepted
                ↓
         (update Status: Accepted, add date)
```

When a phase completes and its ADR is validated:
1. Update status from "Proposed" to "Accepted" in the ADR header (per ADR-00X)
2. Add the acceptance date
3. Include in validation set for subsequent phases

---

## Implementation Principles

### Incremental Approach

- Small, reviewable changes per phase
- Each phase produces **working, testable code**
- Tests accompany implementation (not deferred)
- Compile and test before presenting for review

### Dependency Order

Implement in order of dependencies (matches the ADR-004 layering):
1. Foundation (`logic/`, `parser/`, `lexer/`, `semantics/`, `printer/` — ADR-007 scope; rarely touched)
2. Vague-layer core (`vql/quantifier`, `vql/sampling`, `vql/error`)
3. Typed pipeline (`vql/typed`: catalog, binder, evaluator, model)
4. Facade (`vql/semantics/VagueSemantics`) and examples
5. Docs (ADRs, `VagueQuantifiers.md`, `README.md`)

---

## Communication Format

### Phase Presentation

```markdown
## Phase X: [Title]

### Objective
[What this phase accomplishes]

### ADR References
[Which proposals this implements]

### ADR Compliance Review (Planning Phase)
**Per-ADR verdict:** [a table covering EVERY active ADR — relevant/not-relevant
+ one-line basis. Not just the ones touched. Mandatory for cross-cutting
changes: typing, layering, error channels, encoding, value boundaries.]
**Deviations detected:** None / [List of deviations with decisions required]
**Alignment notes:** [How this phase aligns with existing ADRs]

### Validation Checklist
- [ ] The records Layer 1 of `vql-adr-constraints` names for this kind of work
      were read in full
- [ ] The per-ADR verdict above covers every record in force
- [ ] Every Layer 3 row naming a record this phase touches was checked
- [ ] Every Layer 4 row that applies names the register record that becomes wrong

### Tasks
1. [Specific task]
2. [Specific task]
...

### Questions for User (if any)
- [Question about ambiguity]

### Approval Checkpoint
- [ ] ADR compliance verified at planning stage
- [ ] Code compiles
- [ ] Tests pass
- [ ] **Integration verified** (see Integration Verification below)
- [ ] User approves
```

### Integration Verification

After implementation, verify that new components are **actually reachable** through the library's public surface:

#### For New Public API (facade methods, typeclasses, catalog parameters):
- [ ] Reachable from a public entry point (`VagueSemantics`, `TypeCatalog`, `FolModel`, …) — not only from internals
- [ ] Covered by at least one end-to-end test through the full pipeline (parse → bind → evaluate), not just a unit test of the new component
- [ ] Documented where consumers look (`README.md` / `VagueQuantifiers.md` / scaladoc)

#### For New Internal Components:
- [ ] Wired into the pipeline that uses them (binder, evaluator, sampler) — no dead code
- [ ] Exercised transitively by an existing or new end-to-end spec

#### Verification Commands:
```bash
# Full cross-platform suite (root aggregates folEngine.jvm and folEngine.js)
sbt test
```

**If any integration check fails, the phase is NOT complete.**

### Completion Report

```markdown
## Phase X Complete

### Implemented
- [List of what was built]

### Files Changed
- `path/to/file.scala` — [description]

### Tests Added
- [Test file and coverage]

### ADR Compliance Review (Post-Implementation)
**Re-validated ADRs:** [all accepted ADRs; list those materially touched]
**Compliance status:** ✅ All ADRs compliant / ⚠️ [Deviations found - see below]
**Issues detected:** None / [List of compliance issues requiring user decision]

### ADR Status
- [Proposal name]: Ready for acceptance / Needs more work

### Ready for Review
[Summary for user to review]
```

---

## Questions Protocol

When agent encounters ambiguity:

1. **Stop implementation** at the unclear point
2. **Present context** — what was being attempted
3. **List options** — if applicable
4. **Ask specific question** — not open-ended
5. **Wait for answer** — do not assume

---

## Checkpoints

User will confirm at these points:

- [ ] Working instructions reviewed and approved
- [ ] Implementation plan reviewed and approved
- [ ] **ADR compliance verified at planning phase** (mandatory before implementation)
- [ ] Each phase completion approved
- [ ] **ADR compliance re-verified post-implementation** (mandatory before phase sign-off)
- [ ] **Agent re-reads `docs/WORKING-INSTRUCTIONS.md` before marking phase complete** (mandatory guardrail)
- [ ] Each ADR acceptance approved
- [ ] Final integration approved

---

## Decision Triggers

**STOP and ASK the user before proceeding** when encountering ANY of these:

1. **Public API changes** — Any change to public signatures, published names, or behavior a library consumer would notice
2. **Workarounds** — `asInstanceOf` or unsafe casts outside the boundaries ADR-015 sanctions, or any other "escape hatch"
3. **New dependencies** — Adding imports from libraries not already in use
4. **Type changes** — Modifying case class fields, adding/removing parameters
5. **Behavioral changes** — Changing how existing code works (not just adding new code)
6. **"It works but..."** — Any solution with tradeoffs, limitations, or caveats
7. **Recursive/complex types** — Types that require special handling for serialization

**Litmus test:** If the change affects anything a user/consumer of the API would notice → ASK FIRST.

**Format for decision requests:** the global `decision-guide` format — what
was being implemented and what problem arose, every viable option with a
concrete example of how it plays out and its pros and cons, your
recommendation labelled as yours, and one closed question. A point with only
one workable answer is not a decision: state it, say what you would do, and
wait for a go.

---

## Memory Enforcement

**Problem:** Agent context can lose track of this document mid-session.

**Mitigation:** User may issue these commands at any time:

- `"Re-read WORKING-INSTRUCTIONS.md"` — Agent must re-read and acknowledge
- `"Decision check"` — Agent must verify current action doesn't require a decision
- `"Protocol check"` — Agent must state which protocol section applies to current work

**Agent self-check:** Before ANY file edit, mentally verify:
1. Is this a decision trigger? → If yes, STOP and ask
2. Does this deviate from an ADR? → If yes, STOP and ask
3. Am I assuming user approval? → If yes, STOP and ask

---

## ADR Deviation Protocol Summary

**Agent must NEVER:**
- Implement code that deviates from accepted ADRs without user approval
- Assume deviation is acceptable without asking
- Proceed with implementation if deviation is detected at planning stage

**Agent must ALWAYS:**
- Review ALL accepted ADRs before proposing any code changes
- Notify user immediately when deviation is detected (planning OR review phase)
- Present clear options and wait for user decision
- Document all deviations and resolutions in phase reports

---

## CRITICAL STOP POINTS

Before ANY of these actions, STOP and ask for explicit approval:
- [ ] Deleting files
- [ ] Removing methods/functions
- [ ] Changing service interfaces
- [ ] Modifying layer wiring
- [ ] Removing tests

Format: "I propose to [ACTION]. Approve? (Y/N)"

---

*Document created: 2026-01-17*  
*Last updated: 2026-08-10 (fully migrated to this repository's ADR corpus and library shape)*  
*Status: Awaiting user approval*
