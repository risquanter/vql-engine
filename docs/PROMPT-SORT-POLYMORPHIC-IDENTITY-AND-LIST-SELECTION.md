# Agent prompt — vql-engine: feasibility and design for sort-polymorphic identity predicates and list-valued selection

You are working in the **vague-quantifier-logic** repository (this repo, the `vql-engine`
artifact). Work only in this repo. Follow its own governance before doing anything: read
`docs/WORKING-INSTRUCTIONS.md`, every ADR under `docs/`, `docs/Architecture.md`, and
`docs/TODOS.md`. The downstream consumer is the **register** project; register's need is described
below as the motivating instance, but the constraint under study is general to the engine, not
specific to register. Current engine version: `0.17.0`.

**This is a phased feasibility-and-design task, not an implementation task.** No source code is
written until a plan is approved. The `/decision-guide` skill is available to you and is the
required presentation format at the decision points named below.

> **Neutrality — read before starting.** This prompt takes **no position** on whether the change
> should be made, and **no position** on which mechanism would implement it. Every candidate
> direction listed later is enumerated for completeness only; the list is deliberately unordered and
> carries no preference. "Keep the engine's type system as it is and require consumers to declare a
> distinct identity predicate per sort" is a first-class option, not a fallback. Your feasibility
> finding and your recommendation are **yours** to derive from the code and the ADRs, and must be
> labelled as yours. Do not treat anything in this prompt as a steer toward a particular answer.
> Where this prompt states what register believes is or is not already expressible, treat that as a
> claim to verify against the code, not as a given.

---

## Exec summary — what this task is

A consumer declares predicate and function symbols by giving each a **signature** whose parameters
are **sorts** (`core/src/main/scala/vql/typed/TypeCatalog.scala`):

```scala
case class FunctionSig(params: List[TypeId], returns: TypeId)
case class PredicateSig(params: List[TypeId])
```

`functions` and `predicates` are `Map`s keyed by symbol name, and `TypeId` is
`opaque type TypeId = String` (`core/src/main/scala/vql/typed/TypeDefs.scala:3`) — a **concrete sort
name with no type-variable form**. Two consequences follow, to be verified against the code:

1. **A symbol has exactly one signature**, therefore one arity and one fixed sequence of parameter
   sorts. A given predicate symbol's first parameter is one concrete sort, not "any sort".
2. **There is no sort polymorphism.** A signature parameter cannot say "an entity of any domain
   sort" or "an entity of sort family F"; it names one declared sort.

This task studies two linked questions that both reduce to the fixed-sort-per-parameter model above:

- **(Primary) Sort-polymorphic identity predicates.** A consumer that models more than one kind of
  first-class entity — say nodes *and* mitigations — and wants to reference an entity **by name** or
  **by id** in the same way for every kind, must today declare a **separate identity predicate symbol
  per entity sort** (one `named` fixed to the node sort, a different symbol for the mitigation sort),
  because one symbol carries one fixed parameter sort. There is no way to write **one** `named`
  predicate and **one** `has_id` predicate that accept an entity of *any* domain sort in their first
  argument, with the name/id literal in the second argument keyed to that entity's sort. The question:
  can the engine's type system and binder support a single identity predicate symbol whose entity
  argument is sort-polymorphic?

- **(Additional) List-valued selection built from name/id references.** Beyond a single entity, the
  consumer wants to reference **a list of entities, each identified by name or id**, as one argument
  value — with the **list as the general/default form**, and the single-entity, empty (none), and
  full (all) cases being degenerate list cases (a one-element list, a distinguished empty/complete
  list). This is the enumerated-selection question already opened in
  `docs/PROMPT-SET-VALUED-ARGUMENT-TERM.md`, now with the added requirement that each element be
  reachable by the same by-name / by-id identity mechanism as a single entity. **Assess explicitly
  whether the two prompts' questions unify** into one selection-sublanguage design or are better kept
  separate; do not duplicate that prompt's work — reference it and state the interaction.

The task has three phases:

1. **Feasibility.** Determine whether the engine's type system, binder, and evaluation pipeline can
   support (a) a sort-polymorphic identity predicate and (b) a list-valued selection whose elements
   are name/id references — without breaking the accepted architecture, and at what cost. Present the
   finding via `/decision-guide` and halt.
2. **Design** (only if Phase 1 concludes it is feasible and the user approves proceeding). Sweep the
   ADRs and design docs for cross-cutting constraints, then present the design choice(s) via
   `/decision-guide` and halt.
3. **Implementation plan.** Turn the approved design into an implementation-grade plan, present it via
   `/decision-guide`, and get approval before any code.

---

## The type and identity machinery today (established facts — verify them, do not assume)

- **Signatures name concrete sorts** — `core/src/main/scala/vql/typed/TypeCatalog.scala`:
  `FunctionSig(params: List[TypeId], returns: TypeId)` and `PredicateSig(params: List[TypeId])`. Each
  parameter is a single `TypeId`. `functions`/`predicates` are `Map`s keyed by symbol name, so one
  symbol → one signature → one arity and one fixed sequence of parameter sorts.

- **`TypeId` is monomorphic** — `core/src/main/scala/vql/typed/TypeDefs.scala:3`,
  `opaque type TypeId = String`. There is no variable, wildcard, or bounded form. A sort is a name.

- **Domain sorts vs value sorts** — `TypeDecl.DomainType(id)` marks a sort that can be quantified over
  and requires a registered element set in `RuntimeModel.domains: Map[TypeId, Set[Value]]` (ADR-014);
  `TypeDecl.ValueType(id)` marks a scalar/computed sort (`TypeDefs.scala:33-35`). A function's
  **return** sort may not be a `DomainType` (`FunctionReturnIsDomainType`, `TypeCatalog.scala:20-23`);
  parameter sorts may be domain sorts.

- **A symbol cannot be both a function and a predicate.** `TypeCatalog` construction rejects a name
  that appears in both `functions` and `predicates` (verify the collision check). This bounds any
  direction that would reuse one symbol across the function/predicate boundary.

- **How name-vs-id disambiguation works today (as a consumer builds it).** register — the consumer —
  declares its identity predicates over the node sort roughly as:
  - `named  → PredicateSig(List(nodeSort, nodeNameLiteralSort))`
  - `has_id → PredicateSig(List(nodeSort, nodeIdLiteralSort))`
  - `eq     → PredicateSig(List(nodeSort, nodeSort))`

  and registers a **literal validator per literal sub-sort** (`TypeCatalog.literalValidators:
  Map[TypeId, String => Option[Any]]`): the `nodeNameLiteralSort` validator resolves a quoted string
  by **name**, the `nodeIdLiteralSort` validator resolves it by **id**. So the *by-name vs by-id*
  choice is encoded as **two predicate symbols distinguished by their second parameter's literal
  sub-sort**, and the first parameter is the fixed node sort. Verify this mechanism against the binder
  and the validator lookup — it is the mechanism the primary question asks to generalize across sorts.

- **The binder** — `core/src/main/scala/vql/typed/QueryBinder.scala` (`bindTermExpected`). A
  `Term.Fn` is bound recursively with an exact arity and return-sort check; a `Term.Const` is checked
  against `catalog.constants` first, then the expected sort's `literalValidators`; a `Term.Var` not
  already bound introduces a new free variable of the expected sort. A predicate atom is checked
  against the single `PredicateSig` for its symbol. Confirm there is no arm that would admit a
  parameter matching more than one sort.

- **Evaluation is pure and synchronous; domains are pre-materialized.** The checker folds each
  quantifier variable over a realized `Set[Value]`; a term evaluates to a `Value(sort, raw)` the
  dispatcher recovers. Nothing on the path is effectful (the separate
  `docs/PROMPT-EFFECTFUL-EVALUATION-DISPATCH.md` studies effectful dispatch — keep the questions
  distinct).

- **Cross-compilation.** The engine is `crossProject(JVMPlatform, JSPlatform)`; anything added to the
  type system, the binder, or the evaluated value representation must cross-compile to Scala.js.

Concrete illustration of the primary gap. To reference "the mitigation named *IT Risk mitigation*"
the same way it references "the node named *IT Risk*", a consumer would want the **same** predicate
symbol to apply to both entity sorts:

```
∃x : node .       named(x, "IT Risk")            ∧ leaf(x)      -- node, works today
∃m : mitigation . named(m, "IT Risk mitigation") ∧ p95(x, m)    -- mitigation, no single 'named' can type this
```

Under one-fixed-sort-per-symbol, the second line cannot reuse the `named` whose first parameter is the
node sort; the consumer must introduce a distinct symbol for the mitigation sort, and the two lines
stop being written with the same token.

---

## The consumer use case (why this question exists)

register uses the engine as an analytics query layer over a tree of risk nodes, declaring function
symbols `p95`, `p99`, `lec` and identity/classification predicates (`named`, `has_id`, `eq`, `leaf`,
`portfolio`) over the node sort. It is extending this layer to **mitigations** — risk-reduction
measures, each applied to some set of nodes — so a query can ask about a node's loss in a chosen
post-mitigation state. That makes **mitigation** a second first-class entity sort in the query
language, and register wants a user to select mitigations exactly the way they already select nodes:

1. **by name** — `named(m, "IT Risk mitigation")`, identical in form to `named(x, "IT Risk")`.
2. **by id** — `has_id(m, "01ARZ…")`, identical in form to `has_id(x, "01ARZ…")`, and rename-stable
   the same way it is for nodes.
3. **a list of specific mitigations**, each named or id'd — e.g. "mitigations *A* and *B*".
4. and two distinguished aggregate selections that are **not** individual mitigations — **none** (raw,
   un-mitigated) and **all** (every mitigation whose resolved scope covers the node).

**register's own assessment, offered as a claim for you to verify or refute — not as a given:**

- register believes the *single-entity by-name / by-id* forms (1 and 2) are today only expressible by
  declaring **separate identity predicate symbols per entity sort**, because one `PredicateSig` fixes
  the entity parameter to one sort and `TypeId` has no polymorphic form. Reusing the **same** `named`
  / `has_id` tokens across node and mitigation sorts — the uniformity register wants — is what it
  believes needs engine support. Verify or refute this from the type system and binder.
- register believes the *list* form (3) needs a set/list-valued argument term, which is the subject of
  `docs/PROMPT-SET-VALUED-ARGUMENT-TERM.md`; here the added requirement is that each element be
  reachable by the same by-name / by-id identity mechanism, and that the **list be the general form**
  with single-entity, none, and all as degenerate cases. Assess whether that unifies with the
  set-valued-argument prompt's design or is a separate concern.
- register believes forms 4 (none / all) are expressible with distinguished named constants or a small
  wrapper on its own side and do **not** need engine work. If your reading shows otherwise, say so.

State the general form of the request plainly: **can one identity predicate symbol accept an entity of
any domain sort (with the name/id literal keyed to that entity's sort), and can a bounded list of such
name/id references be a single type-checked argument value?** register's node-and-mitigation model is
the concrete instance; the questions are about the engine's type system, binder, and term grammar.

Note what is **out of scope for you**: register's own surface syntax (how it spells the aggregate
selections, e.g. a `mitigation("inherent")` / `mitigation("residual")` wrapper), its internal
selection representation, how it keys its result cache, and how it resolves a mitigation's node scope,
are register's to make and are not settled here. **Do not design register's query surface or its
domain model.** Your subject is solely the engine's type system, binder, term grammar, and
evaluated-value representation.

---

## Phase 1 — Feasibility assessment

Determine whether the engine can support (a) a sort-polymorphic identity predicate and (b) a
list-valued selection of name/id references, and present the finding. Answer at least these questions
from the code and the ADRs; do not answer them in this prompt's voice — they are open:

### Primary — sort-polymorphic identity predicate

- **Type system.** What is the minimal way to let one predicate symbol's parameter accept an entity of
  more than one domain sort? Candidate shapes include: a type variable in `PredicateSig` (parametric
  polymorphism); a designated **entity super-sort** that individual domain sorts belong to, so
  `named(e: Entity, …)` types against it; a per-symbol **signature family** (relaxing one signature
  per symbol) keyed on the entity sort; or keeping signatures monomorphic and requiring a distinct
  symbol per sort. For each, what happens to `TypeId` (still an opaque `String`, or a richer type?),
  to `TypeCatalog` validation, and to the `DomainType`/`ValueType` distinction (ADR-014)?
- **The literal sub-sort keying.** Today the by-name/by-id choice is the second parameter's literal
  sub-sort, with a validator registered per sub-sort. If the entity parameter becomes polymorphic,
  how is the correct name/id literal validator selected for the *actual* entity sort at a call site?
  Is the literal sub-sort itself parameterized by the entity sort, or resolved some other way? Name
  the binder arm that would do this.
- **Binder.** How does `bindTermExpected` (and the predicate-atom binding path) type-check a
  polymorphic entity argument and pin the concrete sort for the rest of the atom? What new error
  variants are needed (ambiguous sort, unresolvable entity sort, name/id validator missing for the
  resolved sort)?
- **Function/predicate exclusivity and arity.** Does any candidate collide with the rule that a symbol
  is either a function or a predicate, or with one-signature-per-symbol? State the interaction.

### Additional — list-valued selection of name/id references

- **Relationship to `docs/PROMPT-SET-VALUED-ARGUMENT-TERM.md`.** That prompt asks whether the engine
  can carry a bounded explicit selection of domain elements as one argument. This prompt adds: each
  element identified by name or id via the (possibly polymorphic) identity mechanism, and the list
  treated as the **general form** with single/none/all as degenerate cases. State plainly whether the
  right design is **one** unified selection sublanguage (a list term whose elements are name/id
  references, subsuming the single-element and aggregate cases) or **two** separate features. If
  unified, say what the single design is; if separate, say why and how they compose.
- **List-as-default typing.** If the general argument is a list of entity references, how is "one
  mitigation" (a singleton list), "none" (empty / a distinguished empty), and "all" (a distinguished
  complete list) each typed and bound? Is a singleton list interchangeable with a bare entity term at
  the call site, or are they distinct types the consumer must convert between?
- **Grammar / binder / value.** What changes to `Term`, `TermParser`, `VagueQueryParser`,
  `BoundTerm`, and the evaluated `Value` carry a list of name/id-resolved entities? Is the list a
  ground/argument-only construct or ever a quantifier range? (Cross-reference the set-valued prompt's
  answers; do not re-derive them.)

### Both

- **Blast radius.** Name every file and signature that changes for each direction: `TypeDefs`
  (`TypeId`/`TypeDecl`), `TypeCatalog` (`FunctionSig`/`PredicateSig`/validation),
  `QueryBinder`, `BoundQuery` (`BoundTerm`), `TypedSemantics` (`evalTerm`/`evalAtom`), `RuntimeModel`,
  `MapDispatcher`, `Term`/`TermParser`/`VagueQueryParser`, and any fragment / satisfying-set path.
- **Architecture conflicts.** Read every ADR and state, per relevant ADR, whether a candidate conflicts,
  is neutral, or is supported. At minimum consider ADR-004 (tagless initial architecture), ADR-007
  (OCaml-ported core preservation — is any ported-tier file on the path, and would the change break
  its characteristics?), ADR-014 (domain vs value sorts — a polymorphic entity sort or an entity
  super-sort bears directly on this), ADR-015/016 (value boundaries and carriers), and ADR-001
  (binding). If a polymorphic sort or super-sort concept has no home in ADR-014, that gap is a finding.
- **Backward compatibility.** Existing catalogs, queries, and the pure `MapDispatcher` must keep
  working unchanged, or the migration cost must be stated. Are the changes purely additive for a
  consumer that only ever declares single-sort monomorphic identity predicates?
- **Whether it is worth it.** "Feasible" and "worth doing" are separate findings. State both, weighing
  the type-system change against the alternative of a consumer declaring a distinct identity predicate
  symbol per sort (uniform *concept*, non-uniform *tokens*) and pre-expanding any list on its own side.

**Deliverable for Phase 1:** a `/decision-guide`-formatted write-up whose decision goal is "does the
engine adopt sort-polymorphic identity predicates and/or a list-valued name/id selection, and if so
along which direction" — presenting each genuinely viable direction (including keeping the type system
as it is) with plain-English pros/cons and a concrete worked example of how a query that names a node
and a mitigation with the **same** `named`/`has_id` tokens, and one that selects a two-element list of
mitigations by name, parses, binds, and evaluates under it. Include your explicitly-labelled
recommendation. Then **halt** and wait for the user's decision. Do not proceed to Phase 2 without an
accepted signal.

### Candidate directions — enumerated without preference

This list exists so the feasibility survey is complete. It is unordered and carries no
recommendation. You may add, split, or discard entries as the code dictates.

- **Keep signatures monomorphic.** No engine change. A consumer that wants by-name/by-id over a second
  entity sort declares a distinct identity predicate symbol for that sort (different token), and
  pre-expands any list on its own side. The uniformity loss (different tokens per sort) and any
  expressiveness loss land on the consumer — state them.
- **Parametric polymorphism in signatures.** Introduce a type-variable form so one `named`/`has_id`
  symbol accepts an entity of any (or a bounded family of) domain sort, with the name/id literal
  sub-sort keyed to the resolved entity sort.
- **An entity super-sort.** Introduce a domain-sort hierarchy / super-sort that node and mitigation
  both belong to, so `named(e: Entity, …)` types against the super-sort and refines to the concrete
  sort during binding.
- **Per-symbol signature family.** Relax one-signature-per-symbol so a symbol carries several
  signatures selected by the entity sort at the call site.
- **A list/selection term of name/id references**, unified with or built on
  `docs/PROMPT-SET-VALUED-ARGUMENT-TERM.md`, with the list as the general form and single/none/all as
  degenerate cases.
- **Any other direction the code supports** that you can state and evaluate.

For each, the feasibility survey must include a concrete example of how a query that references a node
and a mitigation with the same identity tokens, and one that selects an explicit two-element
mitigation list by name, parses, binds, and evaluates under it, and what changes — if anything — for a
consumer that only ever uses single-sort, single-element identity predicates.

---

## Phase 2 — Design (only if Phase 1 concludes feasible and the user approves proceeding)

Guide the design of the chosen direction(s), grounded in this repo's constraints:

- Re-read **every** ADR under `docs/` and `docs/Architecture.md`, and identify the cross-cutting
  constraints the chosen direction touches — not only the ADRs named in Phase 1, but any the design
  turns out to implicate. Cite each by number and section.
- Read `docs/TECHNICAL-DEBT.md`, `docs/PROMPT-SET-VALUED-ARGUMENT-TERM.md`, and any `PLAN-*` /
  `DONE_PLAN-*` docs bearing on the type/bind/term path, so the design does not contradict a settled
  decision or duplicate in-flight work. If this design and the set-valued-argument design should
  merge into one, say so and design them together.
- Resolve the concrete design questions the direction raises (the exact `TypeId`/`TypeDecl` or
  super-sort representation; the polymorphic `PredicateSig` shape; how the literal sub-sort is keyed to
  the resolved entity sort; the `BoundTerm` shape and `Value` carrier for a list of references; binder
  error variants; cross-compilation; whether any construct is ground-only or quantifiable).

**Deliverable for Phase 2:** a `/decision-guide`-formatted write-up of the design decision(s), each
with viable options, pros/cons, a concrete example, and your labelled recommendation, plus an
explicit per-ADR alignment statement (compliant / neutral / deviation-awaiting-decision). Then
**halt** for the user's decision.

---

## Phase 3 — Implementation plan

Turn the approved design into an **implementation-grade plan** consistent with
`docs/WORKING-INSTRUCTIONS.md`: exact signatures for every changed function/type, a file inventory by
full repo-relative path, the per-ADR alignment, the list of any open decisions, and the verification
plan (tests to add and the exact commands that must be green, including the cross-compiled JS build).
Present it via `/decision-guide` and **halt** for approval before writing any code.

---

## Downstream contract (register — for context, not a mandate)

register is the consumer that motivates this. Whatever engine API emerges, register's need is to
reference entities of more than one domain sort — nodes and mitigations — with the **same** by-name
and by-id identity forms, and to name a bounded list of such references as one argument. Do not
over-fit the engine API to register's mitigation model: the engine's contract should serve the general
"identity over any domain sort" and "bounded list of references" cases, and register is one consumer
of them. If the feasibility finding is that the type system stays monomorphic and consumers declare a
distinct identity predicate per sort, that is a legitimate outcome and register absorbs the cost on its
side — say so plainly rather than forcing an engine change.

---

## Key files

- `core/src/main/scala/vql/typed/TypeDefs.scala` — `TypeId` (`opaque type … = String`), `TypeDecl`
  (`DomainType`/`ValueType`).
- `core/src/main/scala/vql/typed/TypeCatalog.scala` — `FunctionSig`/`PredicateSig`, catalog validation
  (function/predicate exclusivity, `FunctionReturnIsDomainType`, `literalValidators`).
- `core/src/main/scala/vql/typed/QueryBinder.scala` — `bindTermExpected` and the predicate-atom
  binding path (const/literal/var/fn arms; where a parameter sort is matched).
- `core/src/main/scala/vql/typed/BoundQuery.scala` — `BoundTerm` (`VarRef`, `ConstRef`, `LiteralRef`,
  `FnApp`).
- `core/src/main/scala/vql/typed/TypedSemantics.scala` — `evalTerm`/`evalAtom` and the evaluation walk.
- `core/src/main/scala/vql/typed/RuntimeModel.scala`, `MapDispatcher.scala` — dispatcher contract and
  the map-backed dispatcher consumers build.
- `core/src/main/scala/logic/Term.scala`, `core/src/main/scala/parser/TermParser.scala`,
  `core/src/main/scala/vql/parser/VagueQueryParser.scala` — term grammar and parsing (for the
  list-valued dimension).
- `docs/` — all ADRs, `Architecture.md`, `WORKING-INSTRUCTIONS.md`, `TECHNICAL-DEBT.md`, `TODOS.md`.
- `docs/PROMPT-SET-VALUED-ARGUMENT-TERM.md` — the linked set/list-valued argument question; assess
  unification with this prompt.
- `docs/PROMPT-EFFECTFUL-EVALUATION-DISPATCH.md` — the separate, distinct effectful-dispatch question.
- `build.sbt` — cross-project setup (JVM + JS).
