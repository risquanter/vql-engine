# Agent prompt — vql-engine: feasibility and design for effectful evaluation dispatch

You are working in the **vague-quantifier-logic** repository (this repo, the `vql-engine`
artifact). Work only in this repo. Follow its own governance before doing anything: read
`docs/WORKING-INSTRUCTIONS.md`, every ADR under `docs/`, `docs/Architecture.md`, and
`docs/TODOS.md`. The downstream consumer is the **register** project; register's need is described
below as the motivating instance, but the constraint under study is general to the engine, not
specific to register. Current engine version: `0.17.0`.

**This is a phased feasibility-and-design task, not an implementation task.** No source code is
written until a plan is approved. The `/decision-guide` skill is available to you and is the
required presentation format at the two decision points named below.

> **Neutrality — read before starting.** This prompt takes **no position** on whether the change
> should be made, and **no position** on which mechanism would implement it. Every candidate
> direction listed later is enumerated for completeness only; the list is deliberately unordered and
> carries no preference. "Keep the engine as it is and require consumers to pre-compute" is a
> first-class option, not a fallback. Your feasibility finding and your recommendation are **yours**
> to derive from the code and the ADRs, and must be labelled as yours. Do not treat anything in this
> prompt as a steer toward a particular answer.

---

## Exec summary — what this task is

The engine evaluates a bound query by walking the formula and calling consumer-supplied function and
predicate implementations. Today that entire walk is **synchronous and pure**: it is valued in
`Either[QueryError, …]`, and the consumer's implementations return `Either[String, …]`. A consumer
whose function or predicate values are the product of an **effect** — a computation that must run in
an effect system (I/O, a cache lookup returning a deferred task, a Monte Carlo simulation) — cannot
produce those values from inside the dispatcher, because the dispatcher must hand back a plain value,
not a description of work to run. Such a consumer is therefore forced to compute every value it might
need **before** evaluation starts and pass them in as already-realized data.

The task has three phases:

1. **Feasibility.** Determine whether the engine's evaluation pipeline can support effectful function
   and predicate implementations (compute-on-demand during the walk) without breaking its accepted
   architecture, and at what cost. Present the finding via `/decision-guide` and halt.
2. **Design** (only if Phase 1 concludes it is feasible and the user approves proceeding). Sweep the
   ADRs and design docs for cross-cutting constraints, then present the design choice(s) via
   `/decision-guide` and halt.
3. **Implementation plan.** Turn the approved design into an implementation-grade plan, present it via
   `/decision-guide`, and get approval before any code.

---

## The constraint today (established facts — verify them, do not assume)

The evaluation pipeline is pure and synchronous end to end. The relevant surfaces:

- **The dispatcher contract** — `core/src/main/scala/vql/typed/RuntimeModel.scala`:

  ```scala
  trait RuntimeDispatcher:
    def evalFunction(name: SymbolName, args: List[Value]): Either[String, Any]
    def evalPredicate(name: SymbolName, args: List[Value]): Either[String, Boolean]
    def functionSymbols: Set[SymbolName]
    def predicateSymbols: Set[SymbolName]
  ```

  Both `eval*` methods return a fully-realized `Either`. There is no effect type in either signature.

- **The map-backed dispatcher consumers actually build** —
  `core/src/main/scala/vql/typed/MapDispatcher.scala`:

  ```scala
  case class MapDispatcher(
    predicates: Map[SymbolName, List[Value] => Either[String, Boolean]],
    functions:  Map[SymbolName, List[Value] => Either[String, Any]] = Map.empty)
  ```

  Each implementation is a pure function `List[Value] => Either[String, …]`.

- **The model-checker that drives the dispatcher** —
  `core/src/main/scala/vql/typed/TypedSemantics.scala`. `evaluate`, `satisfyingSet`,
  `collectRangeElements`, `collectSatisfyingSample`, `evalFormula`, `evalAtom`, `evalTerm`, and
  `evalTerms` are all valued in `Either[QueryError, …]`. The walk binds each quantifier variable by
  folding over a pre-materialized `Set[Value]` (`model.domains(sort)`), and at `evalAtom` /
  `evalTerm`'s `FnApp` case it calls `model.dispatcher.evalPredicate` / `evalFunction` and maps a
  `Left` into `QueryError`. The quantifier loops (`Forall.allOf`, `Exists.anyOf`) are
  `@tailrec` over the domain list. Nothing on this path is effectful.

- **The public entry points** — `core/src/main/scala/vql/semantics/VagueSemantics.scala`:
  `evaluateTyped` (line 121) and `satisfyingSet` (line 149). Both are `Either`-valued.

- **Domains are pre-materialized.** `RuntimeModel.domains: Map[TypeId, Set[Value]]` — every element
  of every quantifiable sort is already a realized `Value` before evaluation begins.

- **No effect library is on the classpath.** `build.sbt` declares no ZIO, cats-effect, or comparable
  dependency. The engine is a `crossProject(JVMPlatform, JSPlatform)` (`build.sbt:43`) — anything
  introduced into the evaluation signatures must cross-compile to Scala.js.

Concrete walk-through of the current behaviour. Take `∃x. leaf(x) ∧ gt_loss(p95(x), "1000000")`
over a domain of nodes. The checker folds over `model.domains(nodeSort)`; for each candidate it
evaluates the body; at `p95(x)` it calls `dispatcher.evalFunction("p95", [Value(nodeSort, n)])` and
must receive `Either[String, Any]` **immediately** — the value `p95(n)` has to already exist. There
is no point in the walk at which the checker could suspend to run a computation and resume with its
result.

---

## The consumer use case (why this question exists)

register uses the engine as an analytics query layer over a tree of risk nodes. It declares function
symbols `p95`, `p99`, `lec` whose values are **loss and probability figures produced by Monte Carlo
simulation**. On register's side, simulation is an effect (a ZIO `Task`) whose results are cached in
a content-addressed Merkle tree, so that editing one node re-simulates only that node and its
ancestors. Because the dispatcher must return a realized value, register cannot run the simulation
effect inside the `p95` implementation. It instead runs the simulation for the nodes it needs
**before** constructing the `RuntimeModel`, and passes the results in as a plain
`Map[NodeId, LossDistribution]`; the dispatcher's `p95` then only does a map lookup.

register is extending this analytics layer to **mitigations** (risk-reduction measures applied to
subsets of nodes). The extension wants to ask about the loss of a node **in its post-mitigation
state** — for example `p95` of a node under a specific set of applied mitigations. Expressed in the
query language, this makes the loss functions depend not just on the node but on which mitigations
apply, and a quantified query can range over many mitigation combinations. Under the current pure
dispatcher, register's only route is to pre-compute the simulation results for **every combination
the query could reference** and hand them all in before evaluation. The alternative register would
naturally reach for — compute each combination's result on demand the first time the walk asks for
it, and memoize it — is exactly what the pure dispatcher forbids, because computing it is an effect.

State the general form of the constraint plainly: **any consumer whose function or predicate values
are produced by an effect must fully pre-realize them before evaluation, because the dispatcher has
no effect channel.** register's mitigation analytics is the concrete instance driving the question;
the question itself is about the engine's execution model.

Note what is **out of scope for you**: register's own decision about whether to model post-mitigation
loss as a per-call function argument, a separate result set, or something else is register's to make
and is not settled here. Do not design register's query surface. Your subject is solely the engine's
execution model and whether it can offer effectful dispatch.

---

## Phase 1 — Feasibility assessment

Determine whether the evaluation pipeline can support effectful function/predicate implementations,
and present the finding. Answer at least these questions from the code and the ADRs; do not answer
them in this prompt's voice — they are open:

- **Blast radius.** Which signatures change if `evalFunction`/`evalPredicate` become effectful, and
  how far does the effect thread up through `evalTerm`, `evalAtom`, `evalFormula`, the `Forall`/
  `Exists` `@tailrec` loops, `collectRangeElements`, `collectSatisfyingSample`, `satisfyingSet`,
  `TypedSemantics.evaluate`, and the `VagueSemantics` public entry points? Name every file and
  signature.
- **Effect representation.** The engine has no effect dependency today and must cross-compile to
  Scala.js. What are the candidate ways to represent "may be effectful" (see the enumerated,
  non-preferential list below), and which are even viable under cross-compilation and under the
  engine's dependency-minimalism? Where a candidate needs a new dependency, that is itself a decision
  the user owns.
- **Architecture conflicts.** Read every ADR and state, per relevant ADR, whether a candidate
  direction conflicts, is neutral, or is supported. At minimum consider: ADR-004 (Tagless Initial
  Architecture — operations off the data types; does an effectful evaluator fit the initial-encoding
  separation?), ADR-007 (OCaml-ported core preservation — is any ported-tier file on the path, and
  would the change break characteristics C1–C13?), ADR-003 (HDR deterministic sampling — the
  `HDRSampler` step in `evaluateOverRange`; does introducing an effect change determinism or ordering
  of sampling?), ADR-012 (error-channel policy `require` vs `Either` — how does an effect channel
  compose with the existing `Either[QueryError, …]` result?), and ADR-001/014/015/016 (binding,
  quantifiability, value boundaries — does effectful dispatch touch any typed-value contract?).
- **Backward compatibility.** The existing pure consumers (and the pure `MapDispatcher`) must keep
  working, or the cost of migrating them must be stated. Is a pure dispatcher expressible as a
  degenerate case of the effectful one, or do both have to coexist?
- **Determinism and evaluation order.** The current walk short-circuits (`And` stops on `false`,
  `Exists` stops on the first `true`). Does any candidate change the order or the number of
  implementation calls in a way a consumer could observe, and does that matter for a cached/effectful
  consumer?
- **Whether it is worth it.** "Feasible" and "worth doing" are separate findings. State both.

**Deliverable for Phase 1:** a `/decision-guide`-formatted write-up whose decision goal is
"does the engine adopt effectful evaluation dispatch, and if so along which direction" — presenting
each genuinely viable direction (including keeping the engine pure) with plain-English pros/cons and a
concrete worked example of what each does to the evaluation walk, and your explicitly-labelled
recommendation. Then **halt** and wait for the user's decision. Do not proceed to Phase 2 without an
accepted signal.

### Candidate directions — enumerated without preference

This list exists so the feasibility survey is complete. It is unordered and carries no
recommendation. You may add, split, or discard entries as the code dictates.

- **Keep the engine pure; consumers pre-realize all values.** No engine change. The cost lands
  entirely on the consumer (pre-computation of every referenced value).
- **Make the dispatcher and the evaluation walk effectful** in some effect representation `F[_]`
  (tagless/typeclass-abstracted, a specific effect type, a minimal built-in deferred type, or
  another representation you identify), so implementations compute on demand and the walk threads the
  effect.
- **Two-pass evaluation:** a pure pre-pass that collects the set of `(symbol, args)` applications the
  walk will require, the consumer realizes exactly those effectfully, then the existing pure walk
  runs against the realized values. This keeps the walk pure while removing the "pre-compute
  everything the query *could* touch" over-computation.
- **Any other direction the code supports** that you can state and evaluate.

For each, the feasibility survey must include a concrete example of how
`∃x. leaf(x) ∧ gt_loss(p95(x), "1000000")` (or a mitigation-flavoured variant) evaluates under it,
and what changes for a consumer whose `p95` is effectful.

---

## Phase 2 — Design (only if Phase 1 concludes feasible and the user approves proceeding)

Guide the design of the chosen direction, grounded in this repo's constraints:

- Re-read **every** ADR under `docs/` and `docs/Architecture.md`, and identify the cross-cutting
  constraints the chosen direction touches — not only the ADRs named in Phase 1, but any the design
  turns out to implicate. Cite each by number and section.
- Read `docs/TECHNICAL-DEBT.md` and any `PLAN-*` / `DONE_PLAN-*` docs that bear on the evaluation
  path, so the design does not contradict a settled decision or duplicate in-flight work.
- Resolve the concrete design questions the direction raises (the exact shape of the effectful
  dispatcher contract; how the pure path is preserved or migrated; how the effect composes with
  `QueryError`; cross-compilation of the effect representation; determinism/ordering guarantees the
  engine will document).

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
supply function/predicate values that are the product of an effect (a cached Monte Carlo simulation)
**without having to pre-realize every value the query might reference**. Do not over-fit the engine
API to register: the engine's contract should serve the general "effectful implementation" case, and
register is one consumer of it. If the feasibility finding is that the engine stays pure, that is a
legitimate outcome and register absorbs the cost on its side — say so plainly rather than forcing an
engine change.

---

## Key files

- `core/src/main/scala/vql/typed/RuntimeModel.scala` — `RuntimeDispatcher` trait, `RuntimeModel`.
- `core/src/main/scala/vql/typed/MapDispatcher.scala` — the map-backed dispatcher consumers build.
- `core/src/main/scala/vql/typed/TypedSemantics.scala` — the model-checking walk (all `Either`-valued).
- `core/src/main/scala/vql/semantics/VagueSemantics.scala` — public `evaluateTyped` / `satisfyingSet`.
- `core/src/main/scala/vql/typed/FolModel.scala`, `TypeCatalog.scala`, `TypeDefs.scala` — catalog/model validation and sort declarations.
- `docs/` — all ADRs, `Architecture.md`, `WORKING-INSTRUCTIONS.md`, `TECHNICAL-DEBT.md`, `TODOS.md`.
- `build.sbt` — cross-project setup (JVM + JS); current absence of any effect dependency.
