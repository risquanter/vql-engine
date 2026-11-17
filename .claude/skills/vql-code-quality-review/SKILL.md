---
name: vql-code-quality-review
description: "vql-engine's code review criteria for its Scala 3 library, cross-built for the JVM and Scala.js — criteria only, carrying no process rules and performing no review. Read by code-review-routine, code-review-complex and security-review-deep; the process rules are in review-protocol. Covers: domain primitive typing, ADR compliance, type safety, functional design, the published API surface, duplication, the library's secure design and its security checklist, compiler hygiene, test quality, comments, plan fidelity, placement."
user-invocable: true
argument-hint: "files or diff to review (attach changed files, or describe the scope)"
---

# Code Quality Review — vql-engine

These are vql-engine's review criteria. This document performs no review and
writes nothing. The process rules — the write boundary, the halt after
presenting, the decision-guide vehicle, the output contract — are in
`~/.claude/skills/review-protocol/SKILL.md`. A review is run by
`/code-review-routine`, `/code-review-complex` or `/security-review-deep`,
which read these criteria and apply them.

For each criterion a review reports one of:

- **PASS** — no issues found
- **FINDING (severity)** — issue, location, concrete fix

Severities: **MUST-FIX** (blocks commit) · **SHOULD-FIX** (quality debt) · **NOTE**

Severity is the reviewer's to assess. The disposition of every finding is the
user's.

**A convention that conflicts with code hygiene is a real finding.** Raise it
in decision-guide format, with an explicit cleanup-scope option: fix every
occurrence now, naming them, or schedule it. Verify first that the cleaner
alternative builds and passes `sbt test` on both platforms. An apparent bad
convention often encodes a real constraint. The ported foundation is the
standing case: its wildcard imports, exception backtracking and list-as-set
operations are preserved by ADR-007 and are not findings.

The criteria below are not exhaustive. Use general principles and industry best
practices to catch issues the list has not anticipated.

**Accepted risks.** The open items in `docs/TODOS.md` and the entries in
`docs/TECHNICAL-DEBT.md` are this repository's record of known gaps. A finding
that matches one is reported as previously accepted, not as new.

---

## Pass 0a — Domain primitive typing (run first)

For every new field, parameter, case or function parameter in scope:

- **A sort or a symbol is a `TypeId` or a `SymbolName`** from `vql.id`, never
  a `String` (ADR-001, ADR-023). **MUST-FIX.**
- **Caller text in a refusal or a bind error is a bounded text type**:
  `WordText`, `DecimalText`, `LiteralText`, `VariableText` or `SymbolText` from
  `lexer/TokenText.scala`, or a `TokenKind`, which carries no text for an
  unknown operator or a stray character (ADR-022 §2). A `String` field holding
  any part of the caller's query is an unbounded carrier. **MUST-FIX.**
- **Every new type carries its family**: the existing family it joins, the
  file that family lives in with one existing member quoted, and the existing
  type it reuses or the statement that none denotes the same concept
  (`CLAUDE.md`, the plan gate, item 1). The search and the families are in
  "Family search" below. A new type that restates an existing one under
  another name is a duplicate. **MUST-FIX.**
- **A raw primitive in a signature carries its reason.** A `String`, `Int` or
  `Any` in a new or changed signature names the opaque type that replaces it,
  or why none applies. `Any` is accepted for a carrier, which ADR-015 erases at
  `Value.raw`, `LiteralRef.value` and `evalFunction`'s result. **MUST-FIX**
  when the reason is missing.
- **A bare literal that belongs to a fixed set** is a case of that set's
  `enum`. A single constant is a named value in the companion of the type that
  owns it, with a note saying where the number comes from. ADR-024's
  `MaxParenthesisDepth` and `MaxOperators` are the precedent. **SHOULD-FIX.**
- **`Option[T] = None` as a default parameter.** The `Option` stays and the
  default goes. **MUST-FIX.**
- **`TypeCatalog.unsafe` outside a test or an application's startup.** The
  catalog's validated factory returns `Either` (ADR-001 §1). **MUST-FIX.**

### Family search

The answers come from the repository, never from this table. A table goes stale
as the code moves, and the code does not. The table names where each family
lived when it was written, and the command that finds its members now. Run
from the repository root.

| Family | File | One member | Naming pattern | Search |
|---|---|---|---|---|
| Identifier | `core/src/main/scala/vql/id/Identifiers.scala` | `opaque type TypeId = String` | the concept's name; `apply` with a `require`; an alias and a forwarding `apply` in `vql/typed/TypeDefs.scala` (ADR-023 §1) | `grep -rn "opaque type" core/src/main/scala/vql/id` |
| Bounded text | `core/src/main/scala/lexer/TokenText.scala` | `opaque type WordText = String` | `<Kind>Text`; a `require` on the alphabet, or an `enum` with `of` for a name built from them (`VariableText`) | `grep -n "opaque type\|^enum" core/src/main/scala/lexer/TokenText.scala` |
| Failure, refusal or error | `vql/error/`, `parser/`, `lexer/LexFailure.scala`, and `TypeCatalogError` in `vql/typed/TypeCatalog.scala` | `enum NestingFailure:` with `case TooManyOperators(limit: Int)` | `<Subject>Failure`, `<Subject>Refusal`, `<Subject>Error` or `<Subject>Problem`; one case per sentence; a derived `message` (ADR-022 §1, ADR-006 §3) | `grep -rn "enum .*\(Error\|Failure\|Refusal\|Problem\)" core/src/main/scala` |
| Check result | `core/src/main/scala/vql/fragment/Fragment.scala` | `enum FragmentViolation:` | `<Check>Violation`, beside its check | `grep -rn "enum .*Violation" core/src/main/scala` |
| Typed IL | `core/src/main/scala/vql/typed/BoundQuery.scala` | `enum BoundFormula:` | `Bound<Node>` | `grep -n "^enum\|^case class" core/src/main/scala/vql/typed/BoundQuery.scala` |
| Value-boundary typeclass | `core/src/main/scala/vql/typed/Extract.scala`, `LiteralParser.scala` | `trait Extract[A]:` | a `trait` over `A`, with library `given`s in its companion (ADR-015) | `grep -rn "^trait [A-Z].*\[A\]" core/src/main/scala` |
| Model and catalog | `core/src/main/scala/vql/typed/RuntimeModel.scala`, `TypeCatalog.scala` | `final case class OrderedDomain private (carriers: Vector[Any])` | valid by construction: a private constructor and a factory (ADR-001 §1) | `grep -rn "case class" core/src/main/scala/vql/typed` |
| Operations | one object per subject, in its package | `object FragmentCheck` | `<Subject>Semantics`, `<Subject>Check`, `<Subject>Binder`, `<Subject>Parser` (ADR-004 §1) | `grep -rn "^object [A-Z]" core/src/main/scala/vql` |

**A set that names the same cases is the same set.** A new `enum` whose cases
name the same limits, sentences or values as an existing one is that `enum`,
not a new family. A new function whose rule is an existing function's rule
applied to another type generalises the existing function.

**Worked example.** T-015 asks for a refusal when an argument list is too long.
The search for the failure family:

```bash
grep -rn "enum .*\(Error\|Failure\|Refusal\|Problem\)" core/src/main/scala
```

returns, among others, `core/src/main/scala/parser/NestingBound.scala` line 8,
`enum NestingFailure:`, whose cases are `ParenthesesTooDeep(limit: Int)` and
`TooManyOperators(limit: Int)`. The answers follow from it:
- **The family** is the foundation's failure enums.
- **The file** is `NestingBound.scala`.
- **The pattern** is a case per limit, holding the limit, with its sentence in
  `message`.

The new refusal is therefore `case TooManyArguments(limit: Int)` in
`NestingFailure`, not a new enum. Every consumer that already matches
`NestingFailure` then meets it as one more case.

### `if`/`else` checklist — every `if` in a file in scope

1. **Several coupled values from one condition** travel together, in one
   `match` on a tuple or an ADT. **SHOULD-FIX.**
2. **The same predicate repeated across arms** is a classification done
   twice; make it one `match`. **SHOULD-FIX.**
3. **Equality against a named case in order to pick a branch** is matched on
   the cases. **SHOULD-FIX.**
4. **An `Option` or `Either` built by hand.** `if x.isEmpty then None else
   Some(f(x))` is `x.map(f)`. **SHOULD-FIX.**
5. **A guard on what a value is**, inside evaluation or binding, belongs in
   the value's constructor as a `require` (ADR-012 §1). **MUST-FIX.**

A single comparison, range check or predicate stays an `if`.

---

## 1. ADR compliance

- Route the change through `vql-adr-constraints` Layer 1, read every record it
  names in full, and give the per-ADR verdict over every record in force
  (`docs/WORKING-INSTRUCTIONS.md`, "Record an explicit per-ADR verdict for every
  active ADR").
- Check every Layer 3 row that names a record the change touches.
- A Layer 4 row that applies names the register record that becomes wrong,
  and that record is written into register's task list once.
- A deviation that no ruling authorized is a decision for the user. **MUST-FIX**
  until ruled.

---

## 2. Type safety

- **A cast outside the boundaries ADR-015 sanctions.** The `Extract[A]`
  instances are the one cast site on the extraction side. A dispatcher reads
  `args(i).extract[A]`. `asInstanceOf` anywhere else is **MUST-FIX**. An
  `isInstanceOf`, or a type test that stands in for one, is **SHOULD-FIX**
  unless the value's type is open by design.
- **A non-exhaustive match**, or a wildcard arm over a closed `enum` that hides
  a future case. **SHOULD-FIX**; **MUST-FIX** on a public error type, because a
  consumer's exhaustive match is where a new case must surface.
- **An `enum` for behaviour, or a `sealed trait` for pure data** (ADR-006).
  **SHOULD-FIX.**

---

## 3. Functional design and the error channel

- **`require` for what a value is, `Either` for what depends on state**
  (ADR-012). A `require` that guards state is **MUST-FIX**, and so is an
  `Either` for a construction invariant.
- **The library has two `Either` boundaries for text**, `FOLParser.parse` and
  `VagueQueryParser.parse`. Vague code that catches a foundation throw anywhere
  else is the "re-wrapping exceptions mid-flight" smell (ADR-002). **MUST-FIX.**
- **Operations live in objects, data is inert** (ADR-004 §1). A method on a
  data type beyond a structural accessor or a derived `message` is
  **SHOULD-FIX**.
- **The binder accumulates only across same-input siblings** (ADR-020).
  Accumulation at an environment-threaded point manufactures cascade errors.
  **MUST-FIX.**
- **Manual unwrap-transform-rewrap** where `map`, `flatMap` or `fold` would do.
  **SHOULD-FIX.**

---

## 4. The published API surface

vql-engine is published to Maven Central, and register pins a released
version. Every public change is a change to a consumer's dependency.

- **A public signature changed, a case added to or removed from a public
  `enum`, or behaviour a consumer relies on changed** — is the version change
  the one `CLAUDE.md`'s table gives, and does the change log say so? **MUST-FIX**
  when the two disagree.
- **Public surface with no caller**, written by the change. **MUST-FIX.**
  Surface that already exists and carries information is raised with both
  options: put it to work, naming what gains, or remove it.
- **A new public entry without an end-to-end test** through parse, bind and
  evaluate (`docs/WORKING-INSTRUCTIONS.md`, "Integration Verification").
  **MUST-FIX.**
- **Code in `core/src/main` that does not cross-build.** Threads, `java.io`,
  `java.nio`, reflection, or a JDK method Scala.js does not implement, breaks
  the Scala.js artifact register parses with. A JVM-only test belongs in
  `core/.jvm/src/test`. **MUST-FIX.**
- **What register must do is named once**, in the plan and in register's task
  list, never as a design constraint on this repository. **SHOULD-FIX** if it is
  missing.

---

## 5. Duplication

- Logic repeated across files or within one.
- A new `enum`, case class or rule that restates an existing one's cases,
  fields or rule under another name. **MUST-FIX.**
- Two implementations of one question. The precedent: range extraction, scope
  evaluation and `satisfyingSet` all evaluate a formula over a set of elements.
  A second implementation of that question must agree with the first and will
  drift from it. **SHOULD-FIX.**

---

## 6. Secure design

**The threat model.** The library has no network surface, no authentication
and no persistence. Its attack surface is its input. A consumer passes text it
received from its own users to `FOLParser.parse` or `VagueQueryParser.parse`,
and evaluates the result against its own model. Register does exactly this,
for stored targeting predicates and for the query pane. The attacker therefore
controls query text, and through it, literal content and the shape and size of
the formula. The library must:

1. **Never let an input end the process.** Every input either parses or
   returns a typed refusal from one of the two `Either` boundaries. A
   `StackOverflowError`, an `OutOfMemoryError` or an exception the boundaries
   do not catch is a crash in the consumer. Inside a ZIO effect under the
   default runtime it ends the process (ADR-024, Context).
   - **A new recursion over tokens or over the parsed tree** is measured
     against ADR-024's limits on a 1 MiB interpreted JVM thread and on Node
     (ADR-024 §5). **MUST-FIX** if it is not.
   - **A grammar construct that adds a tree level without an operator token**
     extends `NestingBound`'s count (ADR-024 §5). **MUST-FIX.**
   - **Argument lists are not yet bounded.** `NestingBound` does not count
     commas, and a long argument list overflows `parseList` on register's
     native image. This is T-015 in `docs/TODOS.md`, an accepted open risk.
2. **Catch only the parsers' own failures.** The boundaries catch
   `LexerError`, `ParseFailure`, `SyntaxError` and, in `VagueQueryParser`,
   `QueryException` (ADR-022 §5). A catch of `Exception` or `Throwable` at a
   boundary reports a defect as the caller's mistake. **MUST-FIX.**
3. **Keep a refusal's content bounded.** A refusal carries a token by its kind,
   and text only in a bounded text type. A quoted literal admits only the
   alphabet `[A-Za-z0-9 /_.+-]` (ADR-022 §2, §3). Widening a text type,
   admitting a character, or adding a field that holds caller text changes
   what register may pass to a client or a log, under register's ADR-035 §5
   (`vql-adr-constraints` Layer 4). **MUST-FIX**, and a Layer 4 entry.
4. **Never substitute text.** A constant-position token reaches the model only
   through the sort's `literalValidators` entry, as a `LiteralRef` or a
   `ConstRef` (ADR-015 §4). Register's injection defence rests on this, under
   register's ADR-029 §3. Library code that builds query text by concatenation
   and parses it again is **MUST-FIX**.
5. **Bound the work an input can cause.** Evaluation costs the domain size times
   the atom count, and each nested quantifier multiplies it (ADR-018, Context).
   A change that makes cost grow faster in the input — a new nested enumeration,
   a sample size that is not bounded by the domain, a regular expression
   applied to caller text that can backtrack — is **MUST-FIX** unless the plan
   states the bound.
6. **Give the same result on every run.** A result, a sample or a reported
   refusal that depends on a `Set`'s iteration order or on a `hashCode` breaks
   reproducibility (ADR-025). A consumer that caches or compares results then
   sees results change at random. **MUST-FIX.**

**Further checks:**

- **No output from library code.** `println`, `System.err` and logging belong
  only in `examples/` and `Main.scala`. A library that writes to a stream it
  does not own leaks its input into the consumer's logs. **MUST-FIX.**
- **No Java serialization, reflection or `Runtime.exec`** in main code.
  **MUST-FIX.**
- **A dispatcher exception propagates.** The engine does not catch an
  exception a consumer's dispatcher throws, by design. A change that starts
  catching it must convert it to a typed failure, never to a refusal of the
  caller's query (ADR-022 §5). **MUST-FIX.**

### OWASP mapping

The items that apply to a parsing and evaluation library:

- **A03:2021 Injection.** Points 4 and 3 above.
- **API4:2023 Unrestricted Resource Consumption.** Points 1 and 5 above. The
  bounds that exist are ADR-024's 64 parentheses and 256 operators and
  quantified variables, applied before parsing.
- **A04:2021 Insecure Design.** Point 6 above, and the rule that a refusal and
  a defect stay distinguishable (point 2).
- **A08:2021 Software and Data Integrity Failures.** The release pipeline:
  `vql-supply-chain` and `docs/RELEASE.md`.

The items that do not apply, with the reason: broken authentication,
authorization at object, property or function level, sensitive business flows,
server-side request forgery, CSRF and the web output paths. The library has no
endpoint, no session, no network call and no rendered output. A change that
introduces any of them reopens the item.

---

## 7. Compiler hygiene

- Compiles on both platforms with no new warning.
- Every import is used. An import outside the ported foundation is a named,
  top-level import (ADR-006 §4, §5).
- A deprecation is addressed, or suppressed with a stated reason.

---

## 8. Test quality

- **New or changed behaviour is tested**: the path that succeeds, every
  validation rule and every refusal.
- **Both platforms run the test.** A test under `core/src/test` runs on the
  JVM and on Node. A test that needs threads or a set stack size is under
  `core/.jvm/src/test`, and its Node counterpart, where one exists, runs
  in the shared tree.
- **No assertion weakened, reframed or removed** without the behaviour it
  pinned being removed. That is decision trigger 8 in `CLAUDE.md`. **MUST-FIX.**
- **No `asInstanceOf` in a fixture.** A test reads a value with `extract[A]`.
  **SHOULD-FIX.**
- **A statistical test has a bounded false-failure rate.** Asserting one sample
  against a fixed range tests one draw, and an unrelated change can move that
  draw out of range. A goodness-of-fit test over many seeds with a stated
  significance level, or a pinned value for reproducibility, tests the
  property. **SHOULD-FIX.**

---

## 9. Comments and documents

- **A comment states current behaviour.** No plan, phase, review or date
  reference, and an ADR only as a trailing pointer after the rule is stated
  (the `code-comment-style` skill). **SHOULD-FIX.**
- **The OCaml source in a ported file's scaladoc stays verbatim** (ADR-007
  C11). Rewriting or trimming it is **MUST-FIX**.
- **A consumer document the change makes stale** — `README.md`,
  `docs/VagueQuantifiers.md`, `docs/Architecture.md`, scaladoc — is named in
  the plan and updated (`CLAUDE.md`, the plan gate, item 7). **SHOULD-FIX.**

---

## 10. Plan fidelity

A **MUST-FIX** criterion by default.

- The plan passes the plan gate's seven items (`CLAUDE.md`).
- Every signature, type, file and placement matches the plan verbatim.
- A file changed that the plan's file inventory does not name is a finding.
- A plan item absent from the change is an incomplete implementation.
- A divergence is resolved by which side a ruling authorized, never by which
  side looks better.

---

## 11. Placement

| New definition | Where it lives |
|---|---|
| A sort or symbol identifier | `vql/id/Identifiers.scala` (ADR-023) |
| An error or refusal type | `vql/error/` (ADR-023), or `parser/` and `lexer/` for a foundation failure (ADR-022) |
| A bounded text type | `lexer/TokenText.scala` (ADR-022) |
| A typed-IL node, catalog or model type | `vql/typed/` (ADR-001) |
| An operation over inert data | a top-level object, beside its siblings (ADR-004) |
| A check result that holds typed-pipeline values | beside its check, as `FragmentViolation` sits beside `FragmentCheck` |

Import direction: the foundation imports nothing from `vql.*`. `vql.id` imports
nothing, `vql.error` imports `vql.id` and the foundation, and `vql.typed`
imports both (ADR-004, ADR-023). A reverse import is **MUST-FIX**.

**A systemic issue** — the same defect in many places — is not fixed piecemeal.
Count the occurrences, name the files, and present one fix in decision-guide
format.
