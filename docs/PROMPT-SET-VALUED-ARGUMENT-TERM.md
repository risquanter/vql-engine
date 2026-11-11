# Agent prompt — vql-engine: feasibility and design for a set-valued (bounded-selection) argument term

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
> carries no preference. "Keep the engine's term grammar as it is and require consumers to express
> any selection with the machinery that already exists" is a first-class option, not a fallback. Your
> feasibility finding and your recommendation are **yours** to derive from the code and the ADRs, and
> must be labelled as yours. Do not treat anything in this prompt as a steer toward a particular
> answer. Where this prompt states what register believes is or is not already expressible, treat
> that as a claim to verify against the code, not as a given.

---

## Exec summary — what this task is

A consumer declares function and predicate symbols whose arguments are **terms**. Today a term can be
exactly one of three things (`core/src/main/scala/logic/Term.scala`): a variable (`Var`), a named
constant or literal (`Const`), or a function application (`Fn`). There is **no term that denotes a
set — a bounded selection of several elements of a domain sort** — as a single argument. A consumer
that wants to pass "these three particular elements of sort S" to a function symbol in one argument
position has no way to write it: it can pass one element (a constant/literal or a bound variable), or
it can quantify a variable over the whole sort, but it cannot name an explicit finite subset as one
argument value.

The task has three phases:

1. **Feasibility.** Determine whether the engine's term grammar, type system, binder, and evaluation
   pipeline can support a set-valued (bounded-selection) argument term — type-checked, bound, and
   delivered to the dispatcher as one argument — without breaking the accepted architecture, and at
   what cost. Present the finding via `/decision-guide` and halt.
2. **Design** (only if Phase 1 concludes it is feasible and the user approves proceeding). Sweep the
   ADRs and design docs for cross-cutting constraints, then present the design choice(s) via
   `/decision-guide` and halt.
3. **Implementation plan.** Turn the approved design into an implementation-grade plan, present it via
   `/decision-guide`, and get approval before any code.

---

## The term and type machinery today (established facts — verify them, do not assume)

- **The term ADT** — `core/src/main/scala/logic/Term.scala`:

  ```scala
  enum Term:
    case Var(name: String)
    case Fn(name: String, args: List[Term])
    case Const(name: String)
  ```

  There is no collection / set / tuple term. An argument is one `Term`; a `Fn`'s `args` is a
  `List[Term]`, but that is the argument *list* of an application, not a single set-valued argument.

- **How a surface token becomes a `Var` vs a `Const`** —
  `core/src/main/scala/parser/TermParser.scala`. A **quoted** string literal (`"…"`) always parses to
  `Term.Const(content)`. A bare word parses to `Term.Const` only if `isConstName` holds
  (all-numeric, or `nil`, or a decimal literal); otherwise it parses to `Term.Var`. So a bare
  alphabetic identifier that is not a bound variable becomes a **free variable**, never a constant.

- **The binder** — `core/src/main/scala/vql/typed/QueryBinder.scala` (`bindTermExpected`). For a
  `Term.Const(name)` at an expected sort it first looks up `catalog.constants`, then the sort's
  `literalValidators`, and if neither matches it fails with `UnknownConstantOrLiteral`. For a
  `Term.Var(name)` not already bound it introduces a **new free variable** of the expected sort. A
  `Term.Fn` is resolved against `catalog.functions` with an exact arity and return-type check.

- **The catalog signatures** — `core/src/main/scala/vql/typed/TypeCatalog.scala`:

  ```scala
  case class FunctionSig(params: List[TypeId], returns: TypeId)
  case class PredicateSig(params: List[TypeId])
  ```

  `functions` and `predicates` are `Map`s keyed by symbol name, so **a symbol has exactly one
  signature and therefore exactly one arity and one fixed sequence of parameter sorts.** Each
  parameter is a single `TypeId`.

- **Domain sorts vs value sorts** — `TypeDecl.DomainType` marks a sort that can be quantified over
  and requires a registered domain in `RuntimeModel.domains: Map[TypeId, Set[Value]]` (ADR-014);
  `TypeDecl.ValueType` marks a scalar/computed sort. A function's **return** sort may not be a
  `DomainType` (`FunctionReturnIsDomainType`, `TypeCatalog.scala:20-23,128-129`); parameter sorts may
  be domain sorts (an element of a quantifiable sort is a legitimate argument).

- **Constants are not enumerated by quantifiers.** `catalog.constants` is a separate `Map` from
  `RuntimeModel.domains`; a named constant is resolved at bind time to a `BoundTerm.ConstRef` and
  evaluated to `Value(sort, name)` (`core/src/main/scala/vql/typed/TypedSemantics.scala:286-289`). It
  is never added to a sort's domain, so `∃x`/`∀x` over that sort do not range over it.

- **Domains are pre-materialized and evaluation is pure/synchronous.** The checker folds each
  quantifier variable over a realized `Set[Value]`; a term evaluates to a `Value` whose `raw` payload
  the dispatcher recovers. Nothing on the path is effectful (a separate prompt,
  `docs/PROMPT-EFFECTFUL-EVALUATION-DISPATCH.md`, studies effectful dispatch; keep the two questions
  distinct).

- **Cross-compilation.** The engine is `crossProject(JVMPlatform, JSPlatform)`; anything added to the
  term grammar, the binder, or the evaluated value representation must cross-compile to Scala.js.

Concrete illustration of the gap. To ask "the loss of node `x` under mitigations `a` and `b`
together", a consumer would want to write something like `p95(x, {a, b})` — one node argument and one
argument denoting the two-element selection. There is no `{a, b}` term. The consumer can write
`p95(x, a)` (one element), or `∃m. … p95(x, m) …` (one element bound by a quantifier ranging over the
whole sort), but it cannot name the explicit pair `{a, b}` as a single argument value.

---

## The consumer use case (why this question exists)

register uses the engine as an analytics query layer over a tree of risk nodes, declaring function
symbols `p95`, `p99`, `lec` whose values are loss and probability figures from Monte Carlo
simulation. register is extending this layer to **mitigations** — risk-reduction measures, each
applied to some set of nodes. It wants the loss functions to be answerable about a node **in a
chosen post-mitigation state**, so the query language needs a way to name **which mitigations apply**
in a given call.

register needs to express the following selections of mitigations at a call site:

1. **none** — the node's raw, un-mitigated loss.
2. **all** — every mitigation whose resolved scope covers this node (a node-dependent set computed by
   register).
3. **one specific mitigation**, named directly (analogous to naming a node by a quoted name).
4. **one mitigation bound by a quantifier** (`∃m`/`∀m`) — a what-if over the mitigation sort.
5. **an explicit enumerated subset** — e.g. exactly mitigations `a` and `b`.

**register's own assessment, offered as a claim for you to verify or refute — not as a given:**
register believes forms 1–4 are expressible with machinery that already exists in the engine — a
named constant for a fixed distinguished token (forms 1 and 2), a sort literal-validator resolving a
quoted reference to one domain element (form 3), and quantifier binding over the mitigation sort
(form 4). register believes **form 5, the enumerated subset, has no term in the grammar to express
it** — which is the request that motivates this task. If your reading of the code shows any of forms
1–4 in fact requires engine work, that finding is in scope and should be surfaced; if form 5 turns
out to be expressible with existing machinery you have not been credited for, that is equally in
scope.

State the general form of the request plainly: **can the engine offer a term that denotes a bounded,
explicit selection of elements of a domain sort, as a single type-checked argument?** register's
enumerated mitigation subset is the concrete instance; the question itself is about the engine's term
grammar and type system.

One interacting fact, stated neutrally for you to weigh, not to resolve in a particular direction:
because a symbol has exactly one signature and arity (`functions` is keyed by name), adding a
selection argument to `p95`/`p99`/`lec` makes them fixed-arity with that argument always present. Any
notion of an "optional" or variadic selection argument is therefore itself a term-and-signature
question the feasibility study should address rather than assume away.

Note what is **out of scope for you**: register's own decision about how it maps these five surface
forms onto its internal selection representation, how it keys its result cache, and how it resolves a
mitigation's node scope, is register's to make and is not settled here. **Do not design register's
query surface or its domain model.** Your subject is solely the engine's term grammar, type system,
binder, and evaluated-value representation, and whether they can carry a set-valued argument.

---

## Phase 1 — Feasibility assessment

Determine whether the engine can support a set-valued (bounded-selection) argument term, and present
the finding. Answer at least these questions from the code and the ADRs; do not answer them in this
prompt's voice — they are open:

- **Grammar and parser.** What is the minimal change to `Term` and to `TermParser` to admit a
  collection term (e.g. a brace- or bracket-delimited list of element terms)? Does it create parsing
  ambiguity with the existing quantifier/answer-tuple syntax or the bracketed-operator handling in
  `VagueQueryParser`? Name the touched productions.
- **Type system.** How is a set-of-`S` typed? Options include a dedicated collection sort parameter,
  a homogeneous-collection `TypeId` derived from an element sort, or something else. How does that
  interact with the `DomainType`/`ValueType` distinction (ADR-014) — is a set of a domain sort itself
  a domain sort (quantifiable) or a value sort, and what does `FunctionReturnIsDomainType` imply for
  set-valued *parameters* vs *returns*? Can `FunctionSig.params` name such a sort?
- **Binder.** How does `bindTermExpected` type-check each element of the collection against the
  element sort, and what error variants are needed (empty set? duplicate elements? mixed sorts?)? Is
  a set literal a `Const`-like construct, a new `BoundTerm` case, or a reuse of an existing case?
- **Evaluated value.** A term evaluates to a `Value(sort, raw)`. What is the `raw` payload of a set
  term, and how does the dispatcher recover it? Does this stay within the existing `Value` contract
  (ADR-015/016) or need a new value shape?
- **Quantification interaction.** Domains are pre-materialized sets of single elements. Does a
  set-valued sort ever need to be quantified over (a variable ranging over subsets), and if so what
  is its domain — or is the set term strictly a ground/argument construct that is never a quantifier
  range? State which, and enforce it.
- **Blast radius.** Name every file and signature that changes: `Term`, `TermParser`,
  `VagueQueryParser`, `TypeCatalog` (`FunctionSig`/validation), `QueryBinder`, `BoundQuery`
  (`BoundTerm`), `TypedSemantics` (`evalTerm`), `RuntimeModel`, `MapDispatcher`, and any fragment /
  satisfying-set path.
- **Architecture conflicts.** Read every ADR and state, per relevant ADR, whether a candidate
  direction conflicts, is neutral, or is supported. At minimum consider ADR-004 (tagless initial
  architecture — operations off the data types), ADR-007 (OCaml-ported core preservation — is any
  ported-tier file on the path, and would the change break its characteristics?), ADR-014
  (domain vs value sorts), ADR-015/016 (value boundaries and carriers), and ADR-001 (binding).
- **Backward compatibility.** Existing queries, catalogs, and the pure `MapDispatcher` must keep
  working unchanged, or the migration cost must be stated. Is the collection term purely additive?
- **Whether it is worth it.** "Feasible" and "worth doing" are separate findings. State both,
  weighing the size of the grammar/type-system change against the alternative of consumers expressing
  any needed selection with existing machinery (see the enumerated directions).

**Deliverable for Phase 1:** a `/decision-guide`-formatted write-up whose decision goal is "does the
engine adopt a set-valued argument term, and if so along which direction" — presenting each genuinely
viable direction (including keeping the grammar as it is) with plain-English pros/cons and a concrete
worked example of how a query using an explicit selection parses, binds, and evaluates under it, and
your explicitly-labelled recommendation. Then **halt** and wait for the user's decision. Do not
proceed to Phase 2 without an accepted signal.

### Candidate directions — enumerated without preference

This list exists so the feasibility survey is complete. It is unordered and carries no
recommendation. You may add, split, or discard entries as the code dictates.

- **Keep the term grammar as it is.** No engine change. Consumers express every selection they need
  with the existing machinery (constants, sort literal-validators, quantifier binding), and any
  enumerated-subset requirement is either pre-expanded on the consumer side (the consumer computes
  the concrete set before evaluation and passes a single distinguished argument that stands for it)
  or declined. The cost and any expressiveness loss land on the consumer — state them.
- **Add a homogeneous set/collection term to the grammar** with a corresponding collection sort or
  element-sort-parameterized type, type-checked in the binder, evaluated to a `Value` carrying the
  element multiset, and delivered to the dispatcher as one argument.
- **Support variadic / repeated arguments for a symbol** (relaxing the single fixed arity per symbol)
  so an explicit selection is passed as trailing repeated arguments rather than one set term.
- **Any other direction the code supports** that you can state and evaluate.

For each, the feasibility survey must include a concrete example of how a query naming an explicit
two-element selection (or a variant) parses, binds, and evaluates under it, and what changes — if
anything — for a consumer that only ever uses single-element arguments.

---

## Phase 2 — Design (only if Phase 1 concludes feasible and the user approves proceeding)

Guide the design of the chosen direction, grounded in this repo's constraints:

- Re-read **every** ADR under `docs/` and `docs/Architecture.md`, and identify the cross-cutting
  constraints the chosen direction touches — not only the ADRs named in Phase 1, but any the design
  turns out to implicate. Cite each by number and section.
- Read `docs/TECHNICAL-DEBT.md` and any `PLAN-*` / `DONE_PLAN-*` docs that bear on the term/type/bind
  path, so the design does not contradict a settled decision or duplicate in-flight work.
- Resolve the concrete design questions the direction raises (the exact grammar production and its
  delimiter; the exact sort/type representation; the `BoundTerm` shape and the `Value` carrier;
  binder error variants; cross-compilation; whether the construct is ground-only or quantifiable).

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
express, at a call site, an explicit finite selection of elements of a domain sort as one argument.
Do not over-fit the engine API to register's mitigation model: the engine's contract should serve the
general "bounded selection over a domain sort" case, and register is one consumer of it. If the
feasibility finding is that the grammar stays as it is and consumers express selection with existing
machinery, that is a legitimate outcome and register absorbs the cost on its side — say so plainly
rather than forcing an engine change.

---

## Key files

- `core/src/main/scala/logic/Term.scala` — the term ADT (`Var`, `Fn`, `Const`).
- `core/src/main/scala/parser/TermParser.scala` — term parsing; the quoted-literal → `Const` and
  bare-word → `Var`/`Const` rules.
- `core/src/main/scala/vql/parser/VagueQueryParser.scala` — the surrounding query parser (quantifier,
  range, scope, answer-tuple productions).
- `core/src/main/scala/vql/typed/TypeCatalog.scala` — `FunctionSig`/`PredicateSig`, sort declarations,
  catalog validation (`FunctionReturnIsDomainType`, domain vs value sorts).
- `core/src/main/scala/vql/typed/QueryBinder.scala` — `bindTermExpected` (const/literal/var/fn arms).
- `core/src/main/scala/vql/typed/BoundQuery.scala` — `BoundTerm` (`VarRef`, `ConstRef`, `LiteralRef`,
  `FnApp`).
- `core/src/main/scala/vql/typed/TypedSemantics.scala` — `evalTerm` and the evaluation walk.
- `core/src/main/scala/vql/typed/RuntimeModel.scala`, `MapDispatcher.scala` — dispatcher contract and
  the map-backed dispatcher consumers build.
- `docs/` — all ADRs, `Architecture.md`, `WORKING-INSTRUCTIONS.md`, `TECHNICAL-DEBT.md`, `TODOS.md`.
- `docs/PROMPT-EFFECTFUL-EVALUATION-DISPATCH.md` — the separate, distinct effectful-dispatch question.
- `build.sbt` — cross-project setup (JVM + JS).
