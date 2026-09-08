# vql-engine breaking changes for register — handoff

**Date:** 2026-08-31
**vql-engine version:** upgrading to `0.17.0` (from `0.16.0`; breaking, pre-1.0
minor bump). Register should pin `0.17.0` when adapting.
**Scope:** one change — `vql.error.BindErrorDetail` gains a case per
`TypeCheckError` variant and drops the `Other` catch-all. Written to be handed to
the register agent as-is.

All references below are register paths under `modules/`. Line numbers may drift;
match on symbol names.

---

## 1. What changed in vql-engine

`BindErrorDetail` (the per-error detail inside `QueryError.BindError.details`,
introduced in 0.15.0 / ADR-019) previously had two cases:

```scala
enum BindErrorDetail:
  case UnparseableConstant(name: String, sortName: String, sourceText: String, rendered: String)
  case Other(rendered: String)   // every non-UnparseableConstant bind error
  def rendered: String
```

It now has one case per `TypeCheckError` variant (11 cases), and `Other` is
**removed**:

```scala
enum BindErrorDetail:
  case UnparseableConstant(name: String, sortName: String, sourceText: String, rendered: String)
  case TypeMismatch(expectedSort: String, actualSort: String, context: String, rendered: String)
  case ConflictingTypes(name: String, leftSort: String, rightSort: String, rendered: String)
  case ArityMismatch(symbol: String, expected: Int, actual: Int, rendered: String)
  case UnknownPredicate(name: String, rendered: String)
  case UnknownFunction(name: String, rendered: String)
  case UnknownConstantOrLiteral(name: String, rendered: String)
  case UnboundAnswerVar(name: String, rendered: String)
  case UnconstrainedVar(name: String, rendered: String)
  case TypeNotQuantifiable(name: String, rendered: String)
  case UnexpectedFreeVar(name: String, rendered: String)
  def rendered: String
```

Rationale (ADR-021): ADR-019 gave only `UnparseableConstant` its fields as data
and flattened the other ten variants to a rendered string, forcing any consumer
that wanted, say, the two sorts of a `TypeMismatch` to parse them back out of the
message. Every variant now crosses the boundary with its fields as primitives
(a `TypeId` still crosses as `TypeId.value`, so `vql.error` still imports nothing
from `vql.typed`). Removing `Other` makes the engine's facade fold exhaustive: a
future `TypeCheckError` variant is a compile error until it is projected, instead
of silently flattening.

**`rendered` is byte-identical to 0.16.0.** The engine's single renderer did not
change. `BindError.messages` / `message` / `context` are unchanged. If register
reads only `detail.rendered` (and `sortName` on `UnparseableConstant`), its
runtime behavior is identical.

---

## 2. What register must fix

### 2a. Any match on `BindErrorDetail.Other` (MUST FIX — compile break)

`Other` no longer exists. Find every `case BindErrorDetail.Other(...)` (or
`case _: BindErrorDetail.Other`) in register's `QueryError` → HTTP mapping
(`AppError.scala`) and its specs, and replace it with one of:

- **Minimal, behavior-preserving:** a wildcard `case _ =>` that keeps the current
  "not a recoverable node reference → BIND_FAILED" outcome. This reproduces the
  old `Other` behavior exactly, because everything that used to be `Other` still
  falls through the wildcard.
- **Recommended if register classifies by kind:** name the specific new cases
  register cares about and keep a wildcard for the rest.

### 2b. Confirm the UNKNOWN_REFERENCE classifier is unaffected (VERIFY)

Register maps `UnparseableConstant` on the `Node` sort to UNKNOWN_REFERENCE, else
BIND_FAILED, via `detail.sortName`. `UnparseableConstant` and its `sortName`
field are **unchanged**. If register's classifier is a `forall` /
`collect` over `details` keyed on `UnparseableConstant` + `sortName`, it needs no
change beyond 2a.

---

## 3. What register may now do (optional, not forced)

Register can stop parsing `rendered` for structured facts and read the fields
directly:

- `ArityMismatch.symbol / expected / actual`
- `TypeMismatch.expectedSort / actualSort / context`
- `ConflictingTypes.name / leftSort / rightSort`
- the `name`-bearing cases (`UnknownPredicate`, `UnknownFunction`,
  `UnknownConstantOrLiteral`, `UnboundAnswerVar`, `UnconstrainedVar`,
  `TypeNotQuantifiable`, `UnexpectedFreeVar`)

None of this is required to compile or to preserve current behavior; it is
available if register wants finer HTTP classification or structured logging.

---

## 4. Adoption checklist

1. Pin vql-engine `0.17.0`.
2. Replace every `BindErrorDetail.Other` match arm (§2a).
3. Re-run register's `QueryError`-mapping specs; confirm UNKNOWN_REFERENCE vs
   BIND_FAILED outcomes are unchanged (§2b).
4. Optionally adopt structured fields (§3).
