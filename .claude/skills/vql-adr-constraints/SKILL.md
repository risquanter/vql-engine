---
name: vql-adr-constraints
description: "Routing reference to vql-engine's architecture decision records: which records each kind of work must honour whether or not the task mentions them, which record governs which subject, where two records meet, and where a record meets a decision record of the downstream consumer register. It routes; it holds none of the records' content. Read the records it names in full — they are the authority. Load before planning, implementing or reviewing any change, and before judging whether a plan is ready."
user-invocable: true
---

# ADR Reference — vql-engine

**This document routes. It holds no rules, no reasons and no code.** Having found the records, read them in full from `docs/` and work from them. They are the authority. This document can be wrong only by pointing at the wrong file.

It has four layers, and they answer four different questions:

1. **By activity** — what must I honour because of the *kind* of work this is, whether or not the task mentions it. **Start here.**
2. **By subject** — which record governs a named topic.
3. **Where records meet** — the pairs whose combination neither record states alone.
4. **Where a record meets the consumer's** — the vql-engine records that a decision record in register depends on, so a change here can make a register record wrong.

**Which records are in force.** A record in `docs/` whose status is `Accepted` is in force and is validated against. A record whose status is `Proposed` is routed here so its content is found, and is validated against only once it is accepted (`docs/WORKING-INSTRUCTIONS.md`, "Proposed ADRs (validate once accepted)"). ADR-016 is the one such record. The records under `docs/archive/` covered the retired untyped backend and are not in force.

**The verdict covers every record.** Layer 1 decides which records are read in full for a change. It does not narrow the planning-phase review: that review records a verdict for every record in force, relevant or not, with a one-line basis (`docs/WORKING-INSTRUCTIONS.md`, "Record an explicit per-ADR verdict for every active ADR"). Layer 1 is what makes the "relevant" column right.

This document does not cover how the system is built (`docs/Architecture.md`), the paper's theory (`docs/VagueQuantifiers.md`), or what is being built (the `PLAN-*.md` files in `docs/`).

---

## Layer 1 — By activity: what the kind of work demands

**Identify the activity, read its *always* column in full, then add any *also* row that applies.** The always column does not depend on the task's wording. A task that says "add a case to `TypeCheckError`" mentions no encoding, no layering and no consumer, and all three bear on it. Searching the task's words finds what it mentions. This layer finds what it implies, which is why it comes first.

### Every edit to a source file, whatever the activity

**Always:** ADR-006 · ADR-004
Import placement and named imports (ADR-006 §4–§5) and the import direction between layers (ADR-004 §2) bear on a file because it is being edited at all.

**Also:** ADR-007 if the file is in its scope tables (Tiers 1–3), which preserves the ported import style and overrides ADR-006 §4–§5 there.

### Editing a file in the OCaml-ported foundation

The files are listed in ADR-007's scope tables: `parser/`, `lexer/`, `logic/`, `util/StringUtil`, `printer/FOLPrinter`, `semantics/FOLSemantics`.

**Always:** ADR-007 · ADR-002 · ADR-012 · ADR-004
Characteristics C1–C13 and the review criterion that benchmarks every change against them (ADR-007), the throw-inside, `Either`-at-the-edge parser discipline (ADR-002), the one place the error-channel policy defers to the port (ADR-012, "Relationship to ADR-007"), and the rule that the foundation imports nothing from `vql.*` (ADR-004).

**Also:** ADR-006 if a primitive carrier is refined into an ADT (C13 requires ADR-006's encoding).

### Changing the vague query grammar or `VagueQueryParser`

**Always:** ADR-002 · ADR-007 · ADR-012 · ADR-017
The single `Either` boundary at `VagueQueryParser.parse` (ADR-002 §2), the frozen combinators it is layered on (ADR-007), the two `Either` boundaries of the library (ADR-012), and the range production and `ParsedQuery.mk`'s surface validation (ADR-017 §4).

**Also:** ADR-018 if the parse tree's shape changes, since fragment membership walks it · ADR-001 if `ParsedQuery` changes, since the binder consumes it.

### Designing or changing a type, an ADT or an error type

**Always:** ADR-006 · ADR-012 · ADR-004
Whether the sum is an `enum` or a `sealed trait`, and how a shared accessor is declared (ADR-006), whether a construction failure is a `require` or an `Either` (ADR-012), and which layer the type may live in given what it imports (ADR-004).

**Also:** ADR-001 if it is a typed-IL node or a catalog type · ADR-015 and ADR-016 if it carries a `Value` or a literal payload · ADR-019 and ADR-021 if it is an error type in `vql.error` or crosses the error/typed boundary · ADR-014 if it partitions types.

### Changing the typed pipeline: catalog, binder or `BoundQuery`

**Always:** ADR-001 · ADR-014 · ADR-015 · ADR-017 · ADR-020 · ADR-012
The catalog as a contract valid by construction and `bind` as the single compilation step (ADR-001), the bind-time quantifiability check that must not be dropped (ADR-014), the literal branch through `literalValidators` and the `ConstRef`/`LiteralRef` split (ADR-015 §4), the range as a formula and its free-variable rules (ADR-017), where the binder accumulates errors and where it must not (ADR-020), and the error channel (ADR-012).

**Also:** ADR-016 if the literal payload's typing changes · ADR-019 and ADR-021 if a `TypeCheckError` variant is added, removed or reshaped, because the facade fold is exhaustive over them.

### Changing evaluation, the runtime model or the dispatcher

**Always:** ADR-001 · ADR-014 · ADR-015 · ADR-017 · ADR-012
Startup validation of dispatcher coverage and domain registration, done once (ADR-001 §4), the startup half of the two orthogonal checks (ADR-014 §3), the extraction boundary `Extract[A]` and the ban on casts at dispatcher call sites (ADR-015), exact range extraction and the satisfying-set contract (ADR-017), and the error channel (ADR-012).

**Also:** ADR-003 if sampling parameters or the scope estimate are involved · ADR-004 if a new operation object is added.

### Changing sampling or estimation

**Always:** ADR-003 · ADR-017 · ADR-012
The HDR generator as the only randomness and the single code path for exact and sampled (ADR-003), the invariant that range extraction is never sampled (ADR-017 §2), and `require` for construction invariants of the parameters (ADR-012).

### Adding or changing a failure, `QueryError` or bind-error detail

**Always:** ADR-012 · ADR-002 · ADR-019 · ADR-020 · ADR-021 · ADR-004 · ADR-006
Which failures are `require` and which are `Either` (ADR-012), where a parser throw is converted (ADR-002), the primitive-only detail type and the fold in the facade (ADR-019), the accumulation sites (ADR-020), one detail case per `TypeCheckError` variant with no catch-all (ADR-021), the import direction `vql.error` may not break (ADR-004), and the abstract shared accessor (ADR-006 §3).

**Also:** Layer 4 — register classifies these failures, so a reshaped field is a consumer break.

### Changing the public API, the facade or anything a consumer notices

**Always:** ADR-001 · ADR-012 · ADR-004
`VagueSemantics` as the canonical entry and `FolModel` as the pre-validated input (ADR-001 §5), the `Either`-only public surface (ADR-012), and the layer the entry sits in (ADR-004).

**Also:** ADR-017 for `satisfyingSet` · ADR-018 for `FragmentCheck` · ADR-019 and ADR-021 for the bind-error contract · Layer 4 for every change, because register is the consumer.

### Changing fragment membership

**Always:** ADR-018 · ADR-007 · ADR-004 · ADR-006
The tree walk over the existing parser's output and its first-violation result (ADR-018), the reason the parser is not forked (ADR-007), the package's import limits (ADR-004), and the encoding of `Fragment` and `FragmentViolation` (ADR-006).

### Writing, amending or retiring a decision record

**Always:** ADR-00X
The structure, sizing and phrasing every record follows, including the timeless context rule and the optional cross-ADR and alternatives sections. Then "Amending the corpus" below.

### Adding or changing a dependency, the build or a release

**Always:** ADR-003
ADR-003 rules out `scala.util.Random`, `java.util.Random` and `commons-math3`. No other record governs dependencies. The release procedure is `docs/RELEASE.md`, which is not a decision record.

---

## Layer 2 — By subject: which record governs what

| Record | File | Status | Governs |
|---|---|---|---|
| ADR-001 | `ADR-001.md` | Accepted | Many-sorted query binding: the validated `TypeCatalog`, `QueryBinder.bind`, the `BoundQuery` typed IL, startup validation of the runtime model, the public entry |
| ADR-002 | `ADR-002.md` | Accepted | Parser-combinator style: tuple-threaded results, throw inside, one `Either` boundary at the edge |
| ADR-003 | `ADR-003.md` | Accepted | HDR deterministic sampling, Fisher-Yates selection, sampling parameters, the Wilson interval, no external randomness or math library |
| ADR-004 | `ADR-004.md` | Accepted | Tagless initial architecture: data as ADTs, operations in objects, the foundation/vague layering and its import direction |
| ADR-006 | `ADR-006.md` | Accepted | ADT encoding (`enum` versus `sealed trait`), derived and stored accessors, enum-variant imports, top-level named imports |
| ADR-007 | `ADR-007.md` | Accepted | Preservation of the OCaml-ported core: characteristics C1–C13, the files in scope, the review criterion |
| ADR-012 | `ADR-012.md` | Accepted | Error channel policy: `require` for construction invariants, `Either` for operational failure, the relationship to the ported core |
| ADR-014 | `ADR-014.md` | Accepted | Domain-type quantifiability: the bind-time and the startup checks and why neither subsumes the other |
| ADR-015 | `ADR-015.md` | Accepted | Symmetric value boundaries: `LiteralParser[A]` for injection, `Extract[A]` for extraction, one declaration site per sort and carrier |
| ADR-016 | `ADR-016-carrier-witness-on-symmetric-value-typeclasses.md` | Proposed | The `Carrier[A]` storage witness for literal payloads, the `ConstRef`/`LiteralRef` split, adoption as a per-phase decision |
| ADR-017 | `ADR-017.md` | Accepted | Formula ranges: extraction over the active domain, closed-world negation, the denominator, range validation, the satisfying-set entry |
| ADR-018 | `ADR-018.md` | Accepted | Structural fragment membership over the parse tree: `Fragment`, `FragmentViolation`, `FragmentCheck.check` |
| ADR-019 | `ADR-019.md` | Accepted | Bind-error detail across the error/typed boundary: primitive fields, one renderer, the fold in the facade. Its one-case shape is superseded by ADR-021 |
| ADR-020 | `ADR-020.md` | Accepted | Binder error accumulation: only across same-input siblings, the strict accumulating product, conflict accumulation in `mergeEnvs` |
| ADR-021 | `ADR-021.md` | Accepted | One `BindErrorDetail` case per `TypeCheckError` variant and an exhaustive facade fold |
| — | `ADR-00X.md` | Active | The authoring template for a record. Not a decision record |

---

## Layer 3 — Where records meet

Each row is a trap that lives between two records: what a reader who knows both separately would still get wrong. It cannot be produced by summarising the corpus, because the information is in neither record's own account of itself.

Read it as an index of collisions, not as a substitute for either record. Where the reconciliation is written into the records, the row says where.

| Records | The trap |
|---|---|
| ADR-002 × ADR-007 × ADR-012 | ADR-012's `Either` rule reads as binding everywhere, and the ported foundation throws by design. Both hold, because they govern disjoint files. The library has exactly two `Either` boundaries, `FOLParser.parse` and `VagueQueryParser.parse`, and vague code calls only the `Either`-returning foundation entry. Wrapping a foundation throw inside vague code is the "re-wrapping exceptions mid-flight" smell. *Stated in ADR-012, "Relationship to ADR-007", and ADR-002's cross-ADR section.* |
| ADR-007 × ADR-006 | ADR-006 §4–§5 forbid opening a foreign wildcard import and require named top-level imports. The ported files keep their wildcard style under ADR-007. Tidying imports in a ported file under ADR-006, or under a general cleanup rule, breaks a preserved characteristic. C13 does admit an `enum` refinement of a primitive carrier inside the port, encoded per ADR-006. *Stated in ADR-006's cross-ADR section and ADR-007 C13.* |
| ADR-007 C11 × the comment-style rule | The global comment-style rule says a comment describes current behaviour in a sentence or two. The ported files carry verbatim OCaml source in their scaladoc, and C11 makes it the traceability link to the textbook. Rewriting or trimming it to fit the comment style deletes the link. `docs/WORKING-INSTRUCTIONS.md` says "never delete or paraphrase them". |
| ADR-001 × ADR-014 | ADR-001 §4 describes the startup check and reads as covering quantifiability. It covers half. The bind-time `TypeNotQuantifiable` check in `QueryBinder` is the other half, and dropping it lets a value-type quantification reach the evaluator as `DomainNotFoundError`. *Stated in ADR-014's cross-ADR section.* |
| ADR-014 × ADR-017 | `satisfyingSet` enumerates a variable's domain outside `QueryBinder.bind`, so the bind-time check does not run for it. ADR-017 §6 replicates `TypeNotQuantifiable` there. Removing that replica as duplicate code reopens the gap. *Stated in ADR-017 §6 and its cross-ADR section.* |
| ADR-003 × ADR-017 | ADR-003 says exact is the degenerate case of sampled, which invites sampling everything for speed. Range extraction must stay exhaustive, because closed-world negation is a complement over the active domain and is wrong over a sample. Sampling is confined to scope evaluation. *Stated in ADR-017 §2.* |
| ADR-015 × ADR-016 | ADR-015 governs the boundary and leaves `LiteralRef.value: Any`. ADR-016 is Proposed, and §5 makes `Carrier[A]` adoption a per-phase decision. Reporting the `Any` payload as a violation applies a record not yet in force. The `ConstRef`/`LiteralRef` split ADR-016 §3 describes is implemented, and ADR-015 §4 states it. *Stated in ADR-016's cross-ADR section and ADR-015's acceptance note.* |
| ADR-004 × ADR-019 × ADR-021 | A consumer needs the sort of a bind error, and the sort is a `TypeId` in `vql.typed`. Moving `TypeCheckError` or `TypeId` into `vql.error` inverts the layering. The sort crosses as `TypeId.value`, a `String`, and the fold runs in the facade, which already imports both layers. *Stated as alternative E3 in ADR-019 and ADR-021.* |
| ADR-019 × ADR-021 | ADR-019 §1 shows a `BindErrorDetail` with an `Other` catch-all. ADR-021 removed it and made the fold exhaustive. Implementing from ADR-019's sketch reintroduces a wildcard that silently drops a new variant's fields. *A superseded-shape marker sits at ADR-019 §1.* |
| ADR-006 §3 × ADR-019 × ADR-021 | `rendered` reads as a derived accessor, which ADR-006 §3 says to declare as a concrete `def` matching on the cases. It is stored, so it is an abstract member the case parameters implement. A concrete `def rendered` clashes with the parameter and does not compile. *Stated in ADR-019 §1 and ADR-021's cross-ADR section.* |
| ADR-020 × ADR-014 × ADR-017 | ADR-020 makes the binder accumulate, which invites extending accumulation to every check. `TypeNotQuantifiable` and the satisfying-formula chain (`UnconstrainedVar` → `UnexpectedFreeVar` → `TypeNotQuantifiable`) run at sequential, environment-threaded points and stay short-circuiting. Accumulating there manufactures cascade errors. *Stated in ADR-020's cross-ADR section.* |
| ADR-017 × ADR-002 | ADR-017 §4 has `ParsedQuery.mk` enforce the range rules by throwing, which reads as breaking ADR-012's `Either` rule for vague code. The throw sits behind `VagueQueryParser.parse`'s single `Either` boundary, under ADR-002. *Stated in ADR-017 §4.* |
| ADR-012 × ADR-018 | `FragmentCheck.check` returns an `Either`, which reads as an operational-failure channel under ADR-012. It is a membership result with a reason. `Screening(k)` carries no `require` on `k` because `check` is total for a negative `k`. *Stated in ADR-018's cross-ADR section.* |

A change that spans two Layer 1 activities is where a new collision is likely. When one is found, write the reconciliation into both records' cross-ADR sections and add a row here.

---

## Layer 4 — Where a record meets the consumer's

register (sibling checkout `/home/danago/projects/register/`, records in `docs/dev/decision-records/`) consumes vql-engine as a published binary. Each row names a register record whose content depends on a vql-engine decision. A vql-engine change that alters that decision makes the register record wrong, and that is register's to amend. The row is written into register's task list once, per the global sibling-repository rule, and the change here goes ahead on its own merits.

| vql-engine | register | The dependency |
|---|---|---|
| ADR-015 §1, §4 | ADR-029 §3, row "FOL `VagueQueryParser.parse`" | register's injection defence states that "node references resolve at bind time through per-sort literal validators … whitelist-constrained, never interpolated". That holds while the binder routes constant-position tokens through `literalValidators` and never through text substitution. A change to the binder's literal branch reaches register's injection boundary. |
| ADR-019 §4, ADR-021 §1 | ADR-028 appendix §5, "Error Responses" | register's refusal table states that `UnknownReferences` means "Every bind error is a quoted node reference no literal validator accepts". register decides that by reading `BindErrorDetail.UnparseableConstant.sortName` against its own sort names. Renaming that field, changing what crosses in it, or splitting `UnparseableConstant` changes which refusal register reports, with no compile error if the field survives under another meaning. |
| ADR-001, ADR-015 | ADR-028 §1–§2, §4 | ADR-028 describes register's integration as a `TypeCatalog` and a `MapDispatcher` evaluated through `VagueSemantics.evaluateTyped`, with every argument read by `extract[A]`, and states that the library is parsed on both platforms (`QueryRequest.resolve` in the cross-compiled `common` module). A change to those entry points, or dropping the Scala.js build, makes those sections wrong. |

---

## The review this skill exists to serve

Routing is step one of a two-step obligation, and both steps are presentations that halt (`CLAUDE.md`, G4 and G6).

**Before any code.** Identify the activity in Layer 1, read every record its *always* column names, and read the *also* rows that apply. Then write the per-ADR verdict over every record in force: relevant or not, compliant or deviating, each with a one-line basis. A deviation is presented as a decision and waits.

**After the implementation.** Re-read the same set against what was written, because an implementation drifts from its own plan in small ways that each look harmless. Check specifically for a constraint satisfied in the plan and lost in the code. Update the implementation tables of any record whose named locations the change moved. Report what you find and wait before calling the phase complete.

## Amending the corpus — what a change to a record owes

A plan that adds, amends or retires a record carries these in the same plan, not as a follow-up.

**A new record is placed in all four layers.** A row in Layer 2, an entry in every Layer 1 activity it bears on, a Layer 3 row for any collision it creates, and a Layer 4 row if a register record depends on it. It is also added to the record table in `docs/Architecture.md`. Layer 1 is the one with no mechanical check.

**An interaction is written into both records and indexed in Layer 3.** The reconciliation goes into each record's cross-ADR section naming the other. The records carry the content. This table carries only its existence.

**A retired or superseded passage is marked where it sits**, as ADR-019 §1 is, not only in a note at the top.

**A status change is recorded here.** A record moving from Proposed to Accepted changes its Layer 2 status and enters the verdict set.

Then run the check below and leave it printing nothing.

## Keeping this document correct

This check prints any record missing from Layer 2, and should print nothing. Run it from the repository root:

```sh
for f in docs/ADR-*.md; do grep -q "\`$(basename "$f")\`" .claude/skills/vql-adr-constraints/SKILL.md || echo "MISSING $f"; done
```

Layer 1 has no mechanical check. A record can be in force, listed in Layer 2, and absent from every activity that should name it. The only guard is that placing a record is part of the change that adds it, which is why the plan gate in `CLAUDE.md` names all four layers.
