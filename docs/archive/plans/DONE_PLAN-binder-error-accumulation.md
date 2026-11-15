# Implementation Plan: Binder Error Accumulation

**Status:** DONE (2026-08-18). Phases 0–2 complete; suite green both platforms (804/804).
**Date:** 2026-08-18
**Target version:** 0.16.0 (follows 0.15.0, published 2026-08-18, commit 02358d0).
**Origin:** Approved follow-up carved out of PLAN-bind-error-sort-fidelity.
`QueryError.BindError` already carries `details: List[BindErrorDetail]`, but the
binder short-circuits, so that list holds at most one element today. This plan
makes the binder collect errors across genuinely independent subtrees so the
list can hold more than one, which is what its type has always promised.
**New ADR created by this plan:** ADR-020 (binder error accumulation — the
accumulate-vs-sequence boundary and the cascade-error policy). Proposed at
Phase 0, Accepted after Phase 1.
**Parent ADRs:** [ADR-004](ADR-004.md) (layering — unchanged),
[ADR-006](ADR-006.md) (encoding — unchanged), [ADR-012](ADR-012.md) (error
channel — unchanged), [ADR-019](ADR-019.md) (structured bind-error detail — this
plan fills the multi-element list ADR-019 already types for).
**Downstream consumer:** register. Its handover (PLAN-bind-error-sort-fidelity
§9) already states its classification runs a `forall` over `details`, which
stays correct whether the list holds one element or many. No register API change
is forced; register re-pins to 0.16.0 to receive complete error lists.

---

## 0. Executive Summary

**What changes and why.** The type checker (`QueryBinder.bind`) reports at most
one error per run. Every combinator is a `for`-comprehension over
`Either[List[TypeCheckError], _]`, and every failure is a one-element list, so
the first failing subtree stops the pass. A query with two independent mistakes —
`big(x, "abc") /\ leaf(x, x)` where `"abc"` is an unparseable `Loss` literal and
`leaf` is arity-wrong — reports only the first. This is inherited behavior, not a
recorded design choice: no ADR governs binder error policy, and the sibling
`TypeCatalog.collectErrors` already accumulates, so the two halves of the type
system are inconsistent.

**Scope of the change.** Accumulate errors at the points where two subtrees are
genuinely independent — they type-check against the *same* starting environment,
not one against the other's output. The binary connectives (`/\`, `\/`, `=>`,
`<=>`) are exactly such points: `bindBinary` already binds both sides from the
same `env`. Points where the environment threads from one result into the next
input (range → scope → answer variables; term after term inside an argument
list; a quantifier body) stay sequential, because a downstream input that
depends on a failed upstream result would produce cascade errors.

**Payoff.** `BindError.details` becomes honestly multi-element; `List[TypeCheckError]`
stops being a dishonest type; the type checker matches `TypeCatalog.collectErrors`;
AC-2 from the sort-fidelity contract (a mixed bind-error list reaches a consumer)
becomes a real end-to-end pipeline test instead of a register-only unit test; and
a user with several independent mistakes sees them in one pass.

**Not in scope.** Accumulating inside argument lists or across the top-level
range → scope → answer-var sequence (both thread the environment — see Decision
1 Option B for why these are deferred). No change to `QueryError`'s shape, to
`BindErrorDetail`, to the facade projection, or to the layering.

---

## 1. ADR Compliance Review (Planning Phase)

Per-ADR verdict over the whole active corpus (WORKING-INSTRUCTIONS requires the
full table for a cross-cutting error-channel change).

| ADR | Verdict | Basis |
|---|---|---|
| ADR-001 (many-sorted binding) | Relevant, compliant | Same typed IL, same `BoundQuery`; only the number of errors returned on the left changes, never the right (success) path. |
| ADR-002 (parser-combinator style) | Not relevant | No parser change. |
| ADR-003 (HDR sampling) | Not relevant | Bind phase only; sampling untouched. |
| ADR-004 (layering) | Relevant, compliant | Accumulation is internal to `vql.typed`; the facade still projects to `BindErrorDetail`; no import direction changes. |
| ADR-006 (enum vs sealed trait) | Not relevant | No new ADT; `TypeCheckError` and `BindErrorDetail` unchanged. |
| ADR-007 (OCaml-ported core) | Not relevant | `QueryBinder` is vague-layer, not in the ported scope tables. |
| ADR-012 (error channel: require vs Either) | Relevant, compliant | Still `Either[List[TypeCheckError], _]` on the left channel; only list length changes. |
| ADR-014 (domain-type quantifiability) | Relevant, compliant | `TypeNotQuantifiable` is raised at sequential points (quantified var, quantifier body); those stay short-circuit, so its emission is unchanged. |
| ADR-015 (symmetric value boundaries) | Not relevant | No new value crossing; the sort still crosses as `TypeId.value` in the facade, unchanged. |
| ADR-017 (formula ranges / satisfying set) | Relevant, compliant | `bindSatisfyingFormula` binds a formula whose connectives will now accumulate; its documented failure order (UnconstrainedVar → UnexpectedFreeVar → TypeNotQuantifiable) is a sequential chain after `bindFormula` returns and is unaffected. Confirm the ordered-failure scaladoc still reads true post-change (doc sweep item). |
| ADR-018 (fragment membership) | Not relevant | Structural check over the parse tree; no binder involvement. |
| ADR-019 (structured bind-error detail) | Relevant, compliant | This plan realizes the multi-element `details` list ADR-019 already types. No shape change; ADR-019's derived `messages` maps over however many details there are. |
| ADR-016 (carrier witness) | Not relevant, Proposed | Value-typeclass concern; untouched. |

**Deviations detected:** None. **New ADR:** ADR-020 records the accumulate-vs-
sequence boundary and the cascade policy, neither of which any existing ADR
states.

---

## 2. Current Behavior (grounding)

`QueryBinder` (`core/src/main/scala/vql/typed/QueryBinder.scala`), error-relevant
structure:

- `bind` — `for`-comprehension: `bindFormula(range)` → quantifiable check →
  `bindFormula(scope, envAfterRange)` → `bindAnswerVars(envAfterScope)`. **Each
  step's input depends on the previous step's output env.** Sequential.
- `bindFormula` — recurses; `And/Or/Imp/Iff` delegate to `bindBinary`.
- `bindBinary(p, q, env, …)` — **binds `p` and `q` both from the same `env`**,
  then `mergeEnvs(leftEnv, rightEnv)`. The two sides are independent inputs.
  Currently a `for`-comprehension, so a `Left` from `p` skips `q` entirely.
- `bindQuantified` — one body; sequential.
- `bindAtom` → `bindTermsExpected` — `foldLeft` over terms **threading env**
  term to term (a term may bind a variable a later term references). Sequential
  by construction.
- `mergeEnvs` — `foldLeft` over keys, `flatMap`-short-circuiting on the first
  `ConflictingTypes`.

The only point where sibling subtrees share one input env and do not feed each
other is `bindBinary`. That is the accumulation site.

---

## 3. Design

### 3.1 The accumulating combinator

Add a strict binary combinator beside the existing sequential `for`-style, over
`Either[List[TypeCheckError], _]`:

```scala
/** Accumulating product: evaluates BOTH sides (no short-circuit) and, on any
  * failure, concatenates every side's errors left-to-right. Use only where the
  * two computations take the same input independently; use `for` where one's
  * input depends on the other's output. */
private def both[A, B](
  ea: Either[List[TypeCheckError], A],
  eb: Either[List[TypeCheckError], B]
): Either[List[TypeCheckError], (A, B)] =
  (ea, eb) match
    case (Right(a), Right(b)) => Right((a, b))
    case (Left(e1), Left(e2)) => Left(e1 ++ e2)
    case (Left(e1), Right(_)) => Left(e1)
    case (Right(_), Left(e2)) => Left(e2)
```

`eb` is strict (evaluated unconditionally) — that is the whole point; a by-name
second argument would re-introduce short-circuiting.

### 3.2 `bindBinary` becomes accumulating

```scala
private def bindBinary(p, q, env, catalog, mk) =
  both(bindFormula(p, env, catalog), bindFormula(q, env, catalog)).flatMap {
    case ((bp, leftEnv), (bq, rightEnv)) =>
      mergeEnvs(leftEnv, rightEnv).map(m => (mk(bp, bq), m))
  }
```

**This is cascade-free.** `q` already binds against `env` (never against `p`'s
output) in the current code, so binding it after `p` fails feeds `q` exactly the
input it would have received anyway. The only new behavior is that `q`'s errors
are now also collected when `p` fails. No spurious follow-on error is possible.
`mergeEnvs` runs only when both sides succeed, so a merge conflict is still
reported against two well-typed sides — unchanged.

`big(x, "abc") /\ leaf(x, x)` now returns
`[UnparseableConstant("abc", Loss, "abc"), ArityMismatch("leaf", 2, …)]` —
both errors, in left-to-right source order.

### 3.3 Everything else stays sequential

`bind` (range/scope/answer vars), `bindQuantified`, and `bindTermsExpected` keep
their `for`/`foldLeft` short-circuit, because each threads the environment: a
downstream computation consumes an env the upstream one produces, so running it
after an upstream failure would bind against an incomplete env and manufacture
errors that are artifacts of the first failure, not independent mistakes.

### 3.4 `mergeEnvs` conflict accumulation (in scope, low-risk)

`mergeEnvs` is already a fold; switch its short-circuit `flatMap` for
accumulation so that two well-typed sides sharing several variables at
conflicting sorts report every conflict, not just the first. Same-input-per-key
independence holds (each key is judged against the same `(left, right)` pair), so
this is cascade-free for the same reason as 3.2. This keeps the whole
binary-connective path uniformly accumulating.

---

## 4. Acceptance Criteria

- **AC-A (the motivating case):** `big(x, "abc") /\ leaf(x, x)` through
  `VagueSemantics.evaluateTyped` (or `bindTyped`) yields a `BindError` whose
  `details` has length 2, in source order, first an `UnparseableConstant` with
  `sortName == "Loss"`, second the `leaf` arity error.
- **AC-B (AC-2 promoted to a pipeline test):** a query mixing one
  node-recoverable `UnparseableConstant` and one genuine type error yields a
  `details` list with both, so register's `forall(nodeRecoverable)` predicate
  evaluates over a real multi-element list produced by the engine — the test the
  sort-fidelity plan deferred to register.
- **AC-C (order and no duplication):** accumulated errors preserve left-to-right
  source order and contain no duplicate entries for a single mistake.
- **AC-D (sequential points unchanged):** a query that fails in the range and
  again in the scope still reports only the range error (range → scope is
  sequential); a single atom `p(x)` with `x` at conflicting sorts across the
  sequence is unaffected. Locks in that accumulation did not leak past its
  boundary.
- **AC-E (mergeEnvs):** `p(x) /\ q(x)` where `p` fixes `x: Loss` and `q` fixes
  `x: Probability`, plus a second such variable, reports both `ConflictingTypes`.
- **AC-F (regression):** all existing single-error specs still pass; the full
  suite is green on JVM and JS.

---

## 5. Phases

- **Phase 0 (docs):** ✅ this plan + ADR-020 (Proposed) + the full-corpus review
  above. Decisions 1–2 ruled by user (Option A / Option B). No code.
- **Phase 1 (code + tests):** ✅ added `both`; rewrote `bindBinary`; accumulated
  `mergeEnvs`; added AC-A…AC-E specs; suite green both platforms (804/804).
- **Phase 2 (release + docs):** ✅ bumped `build.sbt` to 0.16.0; CHANGELOG 0.16.0
  entry (Changed (breaking)); ADR-020 Proposed → Accepted and added to
  WORKING-INSTRUCTIONS validation set; doc sweep (ADR-017 ordered-failure scaladoc
  re-read — unaffected; PLAN-bind-error-sort-fidelity §9 single-error caveat
  updated to "now multi-element"). register re-pins to 0.16.0 (register-side, not
  this repo).

---

## 6. Open Decisions (Phase 0)

Presented to the user in decision-guide format in chat. Recorded here once ruled.

- **Decision 1 — accumulation boundary:** → **ruled Option A** (2026-08-18):
  binary connectives + `mergeEnvs` only. Cascade-free; the env-threaded points
  (top sequence, argument lists, quantifier body) stay sequential. ADR-020's
  cascade policy is therefore "accumulation is confined to same-input siblings,
  where cascades cannot arise."
- **Decision 2 — version classification:** → **ruled Option B** (2026-08-18):
  treat as a breaking change. A consumer that read `details.head` and ignored
  the rest now silently drops later errors. CHANGELOG under "Changed (breaking)"
  with a migration note; 0.15.0 → 0.16.0 (early-semver minor).

---

## 7. Handover / Downstream

register's bind-error classification already runs `details.forall(nodeRecoverable)`
(PLAN-bind-error-sort-fidelity §9). This plan makes that list complete rather than
truncated-to-one; the predicate's meaning is unchanged, but its input is now the
full set of mistakes. No register code change is required; register re-pins to
0.16.0 to stop losing errors after the first.
