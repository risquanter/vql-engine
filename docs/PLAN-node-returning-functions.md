# Implementation Plan: Node-Returning Functions

**Status:** APPROVED (user, 2026-11-14). `NRF-D-1` is ruled A, and no
decision is open.
**Version this was read at:** 0.18.0.
**Target version:** 0.19.0. Under early-semver a pre-1.0 breaking change bumps
the minor version. This plan removes one public enum case, adds a parameter to
another, and changes the field types of three public cases.
**Plan code:** `NRF`, the initials of the filename slug
`node-returning-functions`. Decisions are `NRF-D-<n>`.
**Origin:** register, the downstream consumer, in the repository `register`:
`docs/dev/plans/PLAN-RISKTRANSFORM.md`, §7.6.12 decisions 15, 16 and 14. Those
decisions wait on the release this plan produces. Register's requirements are
restated in §1 as this plan's own, so no label of that plan is used here.
**Records this plan amends:** `docs/ADR-018.md`, `docs/ADR-014.md` and
`docs/ADR-006.md`. It creates no record. Why no new record is needed is stated
in §2.

---

## 0. Executive summary

**What changes.** A function may declare a domain sort as its return sort. The
catalog check that refuses such a declaration, `TypeCatalogError.FunctionReturnIsDomainType`,
is removed. The targeting fragment admits the application of a function the
caller names as admitted, and still refuses every other function. The catalog
derives the set of functions whose return sort is a domain type, so a consumer
passes that set to the fragment check and never writes a list of names by
hand. The two remaining cases of `TypeCatalogError` hold a `TypeId`, a
`SymbolName` and a typed site in place of raw strings, because the file that
declares them is edited by this plan (§2, settled point 5).

**Why.** Register writes a mitigation's targeting predicate in this engine's
language. A position in that predicate that takes a node today reads a quoted
name only, so "every leaf under Servers" has no form that names Servers by its
identifier. Register ruled that a node is written as a function term,
`descendant_of(x, node_id("01HQ…"))`, and that a stored predicate holds
identifiers only. A function returning a node is what this engine does not
allow today.

**What does not change.** The binder already accepts a function term whose
declared return sort matches the position's sort (§4.2). Evaluation already
wraps a function's result in a `Value` of the declared sort (§4.3). No parser
file changes. `node_id` and `node_named` are register's functions and are not
written here.

---

## 1. Requirements

These are restated from register's rulings as this plan's own.

1. **A function may return a domain sort.** `TypeCatalog.apply` accepts
   `FunctionSig(List(nodeIdLiteral), node)` where `node` is declared
   `DomainType(node)`. Today it returns `Left(List(FunctionReturnIsDomainType(…)))`.
2. **The targeting fragment admits node-returning functions and nothing else
   among functions.** `p95`, `p99` and `lec`, which return value sorts, stay
   refused by the fragment. How the fragment tells the two apart is `NRF-D-1`.
3. **A node-returning function is admitted in every node position.** The
   second argument of `descendant_of`, `child_of`, `leaf_descendant_of` and
   `eq` in register's catalog. The engine side of this is that the binder
   accepts a `Term.Fn` wherever the expected sort equals the function's return
   sort, which it does today (§4.2). This plan pins it with a test.
4. **A function's result for an argument that names no element is the
   consumer's.** Register ruled that `node_id` with an identifier no node has
   returns that identifier, so every atom taking it is false. The engine
   wraps whatever the dispatcher returns (§4.3) and checks nothing about
   membership in a domain. This plan pins that with a test and changes nothing.
5. **One release.** Register moves its dependency to 0.19.0 and then does its
   own work (§10).

### Not in scope

- Register's functions `node_id` and `node_named`, its removal of
  `named_risk`, `risk_id` and the quoted name in `eq`, its refusal of a name in
  a stored predicate, and its browser-side rewrite. Each is register's, and
  §10 names them once.
- Tagged literals, `docs/TODOS.md` T-012. Register considered and declined
  that route on 2026-11-13.
- Moving `TypeCatalogError` to `vql.error`. ADR-023 §2 names the errors that
  package declares: "`TypeCheckError` and `RuntimeModelError` are declared in
  `vql.error`". `TypeCatalogError` is not among them and stays in `vql.typed`.
  `PLAN-catalog-errors-in-vql-error.md` moves `TypeCatalogError` to `vql.error`.
- The two Layer 4 rows `docs/TODOS.md` T-013 records as describing register
  before 0.18.0. T-013 stays open and is not part of this plan.

---

## 2. Decisions for the user

No decision is open. `NRF-D-1` is ruled and kept below as the record. Six
points were settled by derivation and are listed after it, each reversible by
one sentence.

### NRF-D-1 — how the fragment check tells a node-returning function from a simulation function

**Goal.** The targeting fragment must admit `node_id(…)` and refuse `p95(…)`.
Both are `Term.Fn` nodes in the parse tree, and the tree carries no sorts. The
decision is where the information that separates them enters the check.

**The moving parts.**

- A *parse tree* is what `FOLParser.parse` returns, a `Formula[FOL]` whose
  atoms hold `Term` values. A `Term.Fn(name, args)` is a function application.
  The tree knows the function's name and nothing else about it.
- A *catalog* (`TypeCatalog`) declares sorts and signatures. It knows that
  `p95` returns `Loss`, a value sort, and that `node_id` would return `Node`, a
  domain sort. `catalog.domainTypes` is the set of domain sorts.
- *Binding* (`QueryBinder.bind`) turns a parse tree into a `BoundFormula`
  against a catalog. After it, every `FnApp` carries its return sort.
- The *fragment check* (`FragmentCheck.check`) is a walk over the parse tree
  that runs before binding, needs no catalog, and returns the first violated
  rule. Register runs it in its cross-compiled `common` module at the request
  boundary, where it holds no tree and no catalog.

**Option A: the fragment carries the admitted functions as a set of symbols,
and the catalog derives that set from return sorts.**

```scala
enum Fragment:
  case Targeting(admittedFunctions: Set[SymbolName])
  case Screening(maxQuantifierDepth: Int)
```

```scala
// TypeCatalog
def domainReturningFunctions: Set[SymbolName]
```

A consumer writes `Fragment.Targeting(catalog.domainReturningFunctions)`. The
check stays a walk over the parse tree. The rule that separates the two kinds
of function is "the return sort is a domain type", stated once in the catalog.
The fragment receives its consequence and holds no rule about sorts.

- You type `descendant_of(x, node_id("01HQ…"))`. The check finds `node_id`
  in the admitted set and walks into its arguments. Accepted.
- You type `gt_loss(p95(x, "inherent"), 5)`. `p95` is not in the set.
  Refused with `FunctionApplicationNotAllowed(p95)`.
- You type `descendant_of(x, node_id(p95(x, "inherent")))`. `node_id` is
  admitted, its argument is walked, `p95` is refused.
- Cost: `Fragment.Targeting` gains a parameter, which is a consumer break.
  Register must build the set at a boundary where it holds no catalog today.
  Its function signatures do not depend on the tree, so it can declare them
  where that boundary can reach them. That is register's work, named in §10.

**Option B: the check runs after binding, over the bound formula, and reads
each application's sort.**

```scala
object FragmentCheck:
  def check(formula: Formula[FOL], fragment: Fragment): Either[FragmentViolation, Unit]
  def checkBound(formula: BoundFormula, catalog: TypeCatalog): Either[FragmentViolation, Unit]
```

`checkBound` admits `FnApp(_, _, sort)` when `catalog.domainTypes.contains(sort)`.
`Fragment.Targeting` keeps its shape and its quantifier rule.

- Same three inputs, same three answers.
- Cost: `vql.fragment` imports `vql.typed`, which ADR-018's layering statement
  excludes today. Register's targeting checks split across two places: the
  quantifier rule at its request boundary, the function rule at bind time on
  the server. A refused function then surfaces as a bind failure of a stored
  predicate instead of a refusal of the request that carried it.

**My recommendation: A.** It holds if register can declare its function
signatures in `common`. I read `RiskTreeKnowledgeBase.schemaFor` in register:
its `functions` map is three fixed signatures, and only `literalValidators`
and the domains read the tree. So the premise holds today, and register's
side of it is one declaration moved.

- Pro: the check stays before binding, where register already runs it and
  refuses a bad predicate with a 400.
- Pro: `vql.fragment` keeps importing no `vql.typed`. It gains `vql.id`, which
  imports nothing.
- Pro: the separating rule is stated in one place, as a rule on types.
- Con: the set is a set of symbols at the point of use. Register's direction
  of 2026-08-10 excluded the simulation functions "grammatically rather than
  by symbol whitelist". Under A the grammar still refuses every function the
  set does not name, and the set is computed from types, not written by hand.
  Register's later ruling leaves the mechanism to this plan. Its §7.6.12
  decision 15 reads: "How the engine tells a node-returning function from a
  simulation function is the engine plan's to decide."
- Why not B: it moves a request-boundary refusal to bind time and adds a
  second check surface for one rule.

**RULED (user, 2026-11-14): A.**

### Settled by derivation

Each of these has one defensible answer under the records and the rulings. Each
is reversible by one sentence from the user.

1. **`TypeCatalogError.FunctionReturnIsDomainType` is deleted**, with the
   branch of `collectErrors` that emits it. Keeping the case with nothing
   emitting it is dead code. Register never references the case.
2. **`FunctionApplicationNotAllowed.function` becomes a `SymbolText`.** The
   field is a function name read from a parsed `Term.Fn`, and ADR-022 §2 says
   "A name in a refusal is a `VariableText` or a `SymbolText`". `SymbolText.of`
   accepts every name `Term.mkFn` admits. Register matches the case with `_`
   and reads no field. The release is a minor bump already.
3. **No new record.** The targeting rule is ADR-018 §3, and this plan changes
   that rule. The guard it removes is cited in `TypeCatalog.scala` as
   "ADR-015 §1 and T-004", and ADR-015 §1 states no such rule. No record in
   force states that a function must return a value sort. ADR-018 is amended
   in place and ADR-006's examples follow the new cases.
4. **The version is 0.19.0.** `CLAUDE.md`'s table: "A public signature
   removed or changed, a case removed … MINOR".
5. **`TypeCatalogError`'s two remaining cases are typed in this plan.**
   `UnknownType(name: String, location: String)` and
   `NameCollision(name: String)` sit in `TypeCatalog.scala`, which Phase 1
   edits. The global `boyscout` skill obliges two things on an edited file,
   in the same edit and not as a follow-up: "A primitive standing in for a
   concept with a natural valid-value subset becomes the refined type", and
   "A user-facing sentence becomes a named template". `name` is a sort or a
   symbol, and `location` is a sentence composed at five sites. The typed
   forms are in §5.1. Register does not reference `TypeCatalogError`, and the
   release is a minor bump already. The one sentence that reverses this is
   "leave the two cases as they are", which also needs the user's waiver of
   the two obligations for this file.
6. **The rule that a function may return a domain sort is stated in ADR-014
   §1.** That section holds the catalog's partition of sorts and an example
   function whose comment reads "returns Loss — fine". After this plan the
   corpus would otherwise state the rule nowhere, because the guard's only
   written reason is `docs/TODOS.md` T-004. The passage also states
   requirement 4: the engine does not check that such a function's result is
   an element of the registered domain.

---

## 3. ADR compliance review (planning phase)

| ADR | Verdict | Basis |
|---|---|---|
| ADR-001 (many-sorted binding) | Relevant, compliant | `TypeCatalog` stays valid by construction; one validation branch is removed and one derived accessor is added. `QueryBinder.bind` is not edited. `BoundTerm.FnApp(name, args, sort)` already carries a domain sort where one is declared. |
| ADR-002 (parser-combinator style) | Not relevant | No parser is edited. |
| ADR-003 (HDR sampling) | Not relevant | No sampling code is touched. |
| ADR-004 (layering) | Relevant, compliant, text stale | `vql.fragment` gains an import of `vql.id` and of `lexer`, both below it. It still imports no `vql.typed`. `TypeCatalog.scala` gains an import of `vql.error.SymbolKind`, the direction ADR-023 §4 states: "`vql.typed` ← imports `vql.error` and `vql.id`". ADR-018 §2 and its cross-ADR row say the fragment package imports only the foundation, which Phase 4 brings to the new state. |
| ADR-006 (enum vs sealed trait) | Relevant, compliant, examples stale | `Fragment`, `FragmentViolation`, `TypeCatalogError` and the new `DeclarationSite` are pure-data `enum`s. `DeclarationSite.label` is a derived accessor under §3. §1's example and the first code smell quote `TypeCatalogError`'s cases as they were; Phase 4 brings both to §5.1. |
| ADR-007 (OCaml-ported core) | Relevant, compliant | No file in its scope tables is edited. `Term.Fn.name` stays a `String` under C8, and the check reads it by pattern matching as today. |
| ADR-012 (error channel) | Relevant, compliant | `check` keeps returning `Either` as a membership result. `TypeCatalog.apply` keeps returning `Either` on construction. No `require` is added. |
| ADR-014 (quantifiability) | Relevant, compliant, **amended** | §1 gains the statement of settled point 6. Both checks stay. `domainTypes` is read by the new accessor and not redefined. A function returning a domain sort does not make a value sort quantifiable, and a variable bound to such a function's result in a quantified position is still checked against `domainTypes`. |
| ADR-015 (value boundaries) | Relevant, compliant, comment stale | The literal branch is not edited. A dispatcher returns `Any` for every function, domain-returning or not, and `Extract[A]` recovers the carrier. The scaladoc on the removed case cites ADR-015 §1 for a rule that record does not state; the case and the citation go together. |
| ADR-016 (carrier witness) | Not relevant, Proposed | `LiteralRef.value` is not touched. |
| ADR-017 (formula ranges) | Relevant, compliant | A range atom may now hold a domain-returning function. §7 already says range extraction may invoke dispatcher functions. `satisfyingSet` is unchanged and is exercised in Phase 3. |
| ADR-018 (fragment membership) | Relevant, **amended** | §1's `Targeting` case gains a parameter. §3's rule changes from "no `Term.Fn`" to "no `Term.Fn` outside the admitted set, and the admitted applications' arguments are walked". §5 holds. Phase 4 amends the record in place. |
| ADR-020 (binder error accumulation) | Not relevant | The binder is not edited. |
| ADR-022 (typed refusals) | Relevant, compliant | `FunctionApplicationNotAllowed` now holds a `SymbolText`, the type §2 names for a function name. No sentence is added. |
| ADR-023 (identifiers in `vql.id`) | Relevant, compliant | `vql.fragment` imports `SymbolName` from `vql.id`, not from `vql.typed`. `TypeCatalogError` names a sort by `TypeId` and a symbol by `SymbolName`. It stays in `vql.typed`, because §2 names `TypeCheckError` and `RuntimeModelError` only. `PLAN-catalog-errors-in-vql-error.md` moves `TypeCatalogError` to `vql.error`. |

**Deviations detected:** one, ADR-018, and it is the plan's purpose. Its form
was `NRF-D-1`, ruled A, and Phase 4 amends the record to that form.
The amendment to ADR-014 adds a statement and contradicts none.

**What the review read.** Every record in force was read in full on
2026-11-14: ADR-001, 002, 003, 004, 006, 007, 012, 014, 015, 017, 018, 020,
022 and 023, and ADR-016 (Proposed). The activities of `vql-adr-constraints`
Layer 1 that this plan falls under: changing fragment membership, changing
the typed pipeline (the catalog), designing or changing a type, changing the
public API, and amending a record.

**Layer 3 rows checked.**

- ADR-012 × ADR-018: `check` still returns a membership result. `Targeting`'s
  new parameter is a set and has no invariant to `require`.
- ADR-001 × ADR-014 and ADR-014 × ADR-017: both quantifiability checks and the
  satisfying-set replica are unchanged.
- ADR-015 × ADR-016: the `Any` payload is not touched.
- ADR-004 × ADR-023: `vql.fragment` reaches `SymbolName` through `vql.id`.
- ADR-007 × ADR-006: no ported file is edited, so no preserved import style is
  touched. The three source files this plan edits follow ADR-006 §4 and §5.
- ADR-007 C12 × ADR-022 and ADR-002 × ADR-007 × ADR-012: no throw and no
  boundary is edited.
- ADR-003 × ADR-017, ADR-017 × ADR-002 and ADR-020 × ADR-014 × ADR-017: range
  extraction, `ParsedQuery.mk` and the binder's accumulation sites are not
  edited.
- A new row is owed, ADR-018 × ADR-014: the fragment admits a function by
  name, and the name comes from a rule on sorts computed in the catalog. A
  reader of ADR-018 alone sees a symbol list; a reader of ADR-014 alone sees no
  fragment. Phase 4 writes the reconciliation into both records' cross-ADR
  sections and adds the row.

**Layer 4.** Register's ADR-029 §3 row for `TargetingPredicate.create` states
"restricted to the targeting fragment (no quantifiers, no function terms)".
This plan makes that false. Register ruled on 2026-11-13 that the record gives
way, and its plan records the amendment as owed by the slice that adopts this
release. Phase 4 adds the row to Layer 4 and writes register's task-list entry
(§10). Rows one to three rest on the literal branch, `UnparseableConstant`,
`TypeCatalog`, `MapDispatcher`, `evaluateTyped`, `extract[A]` and the Scala.js
build, none of which this plan changes. Row four names three changes that make
register's record wrong: "Widening a text type, admitting a character to the
alphabet, or adding a field that holds caller text". This plan narrows
`FunctionApplicationNotAllowed.function` from `String` to `SymbolText` and
does none of the three.

---

## 4. Current behaviour (grounding)

### 4.1 The catalog refuses a domain-returning function

`core/src/main/scala/vql/typed/TypeCatalog.scala`, lines 123 to 129:

```scala
      if !typeIds.contains(sig.returns) then
        unknownTypes += TypeCatalogError.UnknownType(
          sig.returns.value,
          s"function '${sym.value}' return type",
        )
      else if domainTypeIds.contains(sig.returns) then
        unknownTypes += TypeCatalogError.FunctionReturnIsDomainType(sym.value, sig.returns.value)
```

The case is declared at lines 19 to 23 with the comment "Functions must
return `ValueType` sorts. See ADR-015 §1 and T-004." `docs/TODOS.md` T-004
records the reason: when a function's result was a closed `LiteralValue`, a
domain entity had no carrier. ADR-015 replaced that with `Value(sort, raw: Any)`
and the consumer's `Extract[A]`, so the carrier of a domain sort is the
consumer's, as it is for every domain value already in a model's domains.
The reason for the guard no longer exists.

### 4.2 The binder accepts a function in any position whose sort it returns

`core/src/main/scala/vql/typed/QueryBinder.scala`, lines 232 to 243:

```scala
              for
                boundArgs <- bindTermsExpected(args, sig.params, env, catalog)
                _         <-
                  if sig.returns == expected then Right(())
                  else
                    Left(
                      List(
                        TypeCheckError
                          .TypeMismatch(expected, sig.returns, MismatchSite.FunctionReturn(SymbolText.of(name)))
                      )
                    )
              yield (BoundTerm.FnApp(symbol, boundArgs._1, sig.returns), boundArgs._2)
```

The branch compares the return sort with the expected sort and does not ask
whether either is a domain type. A function declared to return `Node` binds
in a `Node` position once the catalog admits the declaration. This is the
premise register's plan listed as unverified. Reading the code verifies it;
Phase 3 pins it.

### 4.3 Evaluation wraps a function's result in its declared sort

`core/src/main/scala/vql/typed/TypedSemantics.scala`, lines 292 to 299:

```scala
      case BoundTerm.FnApp(name, args, sort) =>
        for
          argValues <- evalTerms(args, env, model)
          result    <- model.dispatcher
            .evalFunction(name, argValues)
            .left
            .map(msg => QueryError.EvaluationError(EvaluationFailure.FunctionRefused(name, msg)))
        yield Value(sort, result)
```

No check that `result` is an element of `model.domains(sort)`. A predicate
then reads the value through `extract[A]`. Requirement 4 holds with no change.

### 4.4 The fragment check refuses every function

`core/src/main/scala/vql/fragment/FragmentCheck.scala`, lines 52 to 56:

```scala
  private def firstFn(term: Term): Either[FragmentViolation, Unit] =
    term match
      case Term.Var(_)      => Right(())
      case Term.Const(_)    => Right(())
      case Term.Fn(name, _) => Left(FunctionApplicationNotAllowed(name))
```

`Fragment.Targeting` is a nullary case. `FunctionApplicationNotAllowed`
holds a `String`.

### 4.5 Who reads these types

Register: `modules/common/src/main/scala/com/risquanter/register/domain/data/TargetingPredicate.scala`
line 92 calls `FragmentCheck.check(formula, Fragment.Targeting)`, and line 97
matches `FragmentViolation.FunctionApplicationNotAllowed(_)`. Register declares
three functions in `RiskTreeKnowledgeBase.schemaFor`, all returning value
sorts. Register does not reference `TypeCatalogError`. Inside this
repository, `Fragment` is used by `FragmentCheck` and its spec only.

---

## 5. Target behaviour

### 5.1 The catalog — `core/src/main/scala/vql/typed/TypeCatalog.scala`

The case `FunctionReturnIsDomainType` and its scaladoc are deleted from
`TypeCatalogError`. In `collectErrors`, the `val domainTypeIds` and the
`else if` branch quoted in §4.1 are deleted. The `if` keeps its `UnknownType`
arm.

The two remaining cases and the site they name (§2, settled point 5):

```scala
import vql.error.SymbolKind

/** The place in a catalog declaration that names a type. */
enum DeclarationSite:
  case Constant(name: String)
  case Parameter(symbol: SymbolName, kind: SymbolKind, index: Int)
  case FunctionReturn(function: SymbolName)
  case LiteralValidator

  def label: String = this match
    case Constant(name)                             => s"constant '$name'"
    case Parameter(symbol, SymbolKind.Function, i)  => s"function '${symbol.value}' parameter $i"
    case Parameter(symbol, SymbolKind.Predicate, i) => s"predicate '${symbol.value}' parameter $i"
    case FunctionReturn(function)                   => s"function '${function.value}' return type"
    case LiteralValidator                           => "literal validator"

enum TypeCatalogError:
  /** `typeId` is named at `site` and is not declared in [[TypeCatalog.types]]. */
  case UnknownType(typeId: TypeId, site: DeclarationSite)

  /** `symbol` is declared both as a function and as a predicate. */
  case NameCollision(symbol: SymbolName)
```

`collectErrors` builds a `DeclarationSite` at each of its five sites in place
of the interpolated sentence, and passes the `TypeId` and the `SymbolName` it
already holds in place of `.value`. Four labels are the sentences the sites
compose today. The fifth changes: today's `s"literal validator for
'${s.value}'"` repeats the sort that `UnknownType.typeId` holds, so the label
is `"literal validator"`.

**Family of `DeclarationSite`.** The site enums that say where an error
points. Existing member, in `core/src/main/scala/vql/error/TypeCheckError.scala`:
`enum MismatchSite: case Variable(name: VariableText) …` with
`def label: String`. `MismatchSite` names a term of a query, and
`DeclarationSite` names a slot of a catalog declaration, so no existing type
denotes this concept. It is declared in `TypeCatalog.scala` beside
`TypeCatalogError`, the only type that holds it. `SymbolKind` is reused from
`vql.error`: its scaladoc reads "What kind of catalog symbol a bind error
names", and its cases `Predicate` and `Function` are the two kinds a parameter
site needs.

One derived accessor is added to the class body, beside `domainTypes`:

```scala
  /**
   * Functions whose declared return sort is a domain type. A consumer whose targeting fragment
   * admits exactly these applications passes this set to `Fragment.Targeting` and writes no list of
   * names.
   */
  def domainReturningFunctions: Set[SymbolName] =
    functions.collect { case (sym, sig) if domainTypes.contains(sig.returns) => sym }.toSet
```

**Family.** The derived accessors of `TypeCatalog`, in this file. Existing
member: `def domainTypes: Set[TypeId] = types.collect { case DomainType(typeId) => typeId }`.
Pattern: a `def` over the catalog's own fields, named for what it selects. No
existing member denotes this set.

**Raw primitives in this file's signatures.**

- `DeclarationSite.Constant(name: String)` and `constants: Map[String, TypeId]`.
  The key is matched against `Term.Const.name`, which ADR-007 C8 keeps a
  `String`. A consumer may declare a key outside the literal alphabet, so
  `LiteralText` would throw while an error is being reported.
- `DeclarationSite.Parameter.index: Int`. It is the zero-based position
  `zipWithIndex` yields over `sig.params`. No opaque type for a position
  exists, and `TypeCheckError.ArityMismatch` holds its two counts as `Int`.
- `literalValidators: Map[TypeId, String => Option[Any]]` is not edited.
  ADR-015 §1 fixes that shape: "`literalValidators: Map[TypeId, String =>
  Option[Any]]`".

### 5.2 The fragment — `core/src/main/scala/vql/fragment/Fragment.scala`, `FragmentCheck.scala`

```scala
package vql.fragment

import lexer.SymbolText
import vql.id.SymbolName

enum Fragment:
  /**
   * No quantifier nodes, and no function application outside `admittedFunctions`. Admits variables
   * and inline literals (`Term.Const`). An admitted application's arguments are walked by the same
   * rule, so a refused function inside an admitted one is still refused. `Targeting(Set.empty)`
   * admits no function at all.
   */
  case Targeting(admittedFunctions: Set[SymbolName])

  case Screening(maxQuantifierDepth: Int)   // unchanged

enum FragmentViolation:
  case QuantifierNotAllowed                           // unchanged

  /** Targeting: a `Term.Fn` outside the admitted set is present; `function` is its name. */
  case FunctionApplicationNotAllowed(function: SymbolText)

  case QuantifierDepthExceeded(limit: Int, found: Int) // unchanged
```

**Family of the changed case.** `Fragment`'s cases, in this file. Existing
member: `case Screening(maxQuantifierDepth: Int)`, which already carries its
fragment's one parameter. `SymbolName` is reused from `vql.id`
(`opaque type SymbolName`, `vql/id/Identifiers.scala`); no new type is made.
**Family of the changed field.** `SymbolText` in `lexer/TokenText.scala`,
"A name in a refusal is a `VariableText` or a `SymbolText`" (ADR-022 §2).
`SymbolText.of(name)` throws for a name `Term.mkFn` would have refused, so it
cannot throw on a parsed tree.

`FragmentCheck`:

```scala
  def check(formula: Formula[FOL], fragment: Fragment): Either[FragmentViolation, Unit] =
    fragment match
      case Targeting(admitted) => checkTargeting(formula, admitted)
      case Screening(k)        => …                                   // unchanged

  private def checkTargeting(formula: Formula[FOL], admitted: Set[SymbolName]): Either[FragmentViolation, Unit]
    // as today, threading `admitted` to every atom

  /** The first refused `Term.Fn` in `terms`, left to right, descending into admitted applications. */
  private def firstFnViolation(terms: List[Term], admitted: Set[SymbolName]): Either[FragmentViolation, Unit]

  private def firstFn(term: Term, admitted: Set[SymbolName]): Either[FragmentViolation, Unit] =
    term match
      case Term.Var(_)                                              => Right(())
      case Term.Const(_)                                            => Right(())
      case Term.Fn(name, args) if admitted.contains(SymbolName(name)) => firstFnViolation(args, admitted)
      case Term.Fn(name, _)                                         => Left(FunctionApplicationNotAllowed(SymbolText.of(name)))
```

`SymbolName(name)` requires a non-empty string, and a parsed function name is
never empty. The pre-order, left-to-right order of ADR-018 §2 is unchanged.
The scaladoc of `FragmentCheck`, which says the check "needs no
`TypeCatalog`", is brought to: it needs no catalog, and a consumer that wants
functions admitted computes the set with `TypeCatalog.domainReturningFunctions`.
The scaladoc of `enum Fragment` says membership "depends only on the tree's
shape — no sorts, no model". It is brought to: membership depends on the
tree's shape and on the fragment's own parameters.

### 5.3 Nothing else in the pipeline changes

`QueryBinder`, `TypedSemantics`, `VagueSemantics`, `RuntimeModel`, `FolModel`
and every parser file are not edited. Phase 3 adds tests to them only.

---

## 6. Implementation phases

Each phase compiles and passes the full suite on both platforms before the
next starts. The plan ships whole, as one release.

**Phase 1 — the catalog.**
1. `vql/typed/TypeCatalog.scala` (§5.1).
2. `TypeCatalogSpec`: the test "FunctionReturnIsDomainType emitted when
   function return sort is a DomainType" becomes "a function may declare a
   DomainType return sort" and asserts `Right`. The test "… not emitted when
   … ValueType" keeps its assertion and is renamed "a function may declare a
   ValueType return sort", because the behaviour it asserts still holds. One
   test is added: `domainReturningFunctions` holds exactly the functions whose
   return sort is a `DomainType`, given one of each.
3. `TypeCatalogSpec`, the typed cases. The `NameCollision` test asserts
   `NameCollision(SymbolName("foo"))`. The test "UnknownType carries location
   string for function return type" asserts
   `UnknownType(TypeId("Ghost"), DeclarationSite.FunctionReturn(SymbolName("p95")))`
   and that the site's `label` is `"function 'p95' return type"`. The test
   "UnknownType emitted separately per site" asserts one
   `DeclarationSite.Parameter` of each `SymbolKind` in place of the two
   `location.contains` checks. One test is added: an undeclared sort under a
   constant and under a literal validator yields `DeclarationSite.Constant`
   and `DeclarationSite.LiteralValidator`.

**Phase 2 — the fragment.**
1. `vql/fragment/Fragment.scala`, `vql/fragment/FragmentCheck.scala` (§5.2).
2. `FragmentCheckSpec`: every `Fragment.Targeting` becomes
   `Fragment.Targeting(Set.empty)`, and every expected
   `FunctionApplicationNotAllowed("f")` becomes
   `FunctionApplicationNotAllowed(SymbolText.of("f"))`. Four tests are added:
   an admitted application is accepted; a refused function inside an admitted
   one is refused and named; an admitted application in the left operand does
   not hide a quantifier in the right; end to end, `FOLParser.parse` of
   `eq(x, node_id("01HQ"))` is accepted with `Set(SymbolName("node_id"))` and
   refused with `Set.empty`.

**Phase 3 — the binder and the evaluator, pinned.**
1. `QueryBinderSpec`: a catalog declaring `DomainType(asset)`,
   `ValueType(assetId)` with a literal validator for `assetId`, and
   `SymbolName("asset_with_id") -> FunctionSig(List(assetId), asset)`. Binding
   `same(x, asset_with_id("a2"))` with `same: (asset, asset)` yields a
   `BoundTerm.FnApp(_, _, asset)` in the second position and binds `x` to
   `asset`. Binding `gt_loss(asset_with_id("a2"), 5)` yields `TypeMismatch`
   at `MismatchSite.FunctionReturn`.
2. `VagueSemanticsTypedSpec`, end to end through parse, bind and evaluate:
   `VagueQueryParser.parse("""Q[>=]^{1/1} x (same(x, asset_with_id("a2")), critical(x))""")`
   over domain `{a1, a2, a3}`, a dispatcher whose `asset_with_id` returns its
   argument's raw `String` and whose `same` compares the two raw values. The
   range is `{a2}`. `satisfyingSet` of `same(x, asset_with_id("a2"))` is
   `{Value(asset, "a2")}`, and of `same(x, asset_with_id("zz"))` is the empty
   set, with no error. The second pins requirement 4.

**Phase 4 — documents, version, the register entry.**
1. `docs/ADR-018.md`, every passage the ruled rule makes false:
   - §1's code block, to the two enums of §5.2.
   - §2's sentence that `check` "imports only the foundation", to: it imports
     the foundation and `vql.id`.
   - §3's heading and rule, to: no `Term.Fn` outside the admitted set, and an
     admitted application's arguments are walked by the same rule.
   - The code smell "Forking the parser", whose good example passes the
     nullary `Fragment.Targeting`.
   - The code smell "Rejecting inline literals", whose two examples build
     `FunctionApplicationNotAllowed` from a `String`.
   - The cross-ADR rows for ADR-004 and ADR-014. The ADR-014 row carries the
     reconciliation of the new Layer 3 row.
   - The implementation table is unchanged.
2. `docs/ADR-006.md`: §1's example and the first code smell show
   `TypeCatalogError` as §5.1 declares it.
3. `docs/ADR-014.md`: §1 gains the statement of §2, settled point 6. The
   cross-ADR section gains a line naming ADR-018, the reconciliation of the
   new Layer 3 row.
4. `.claude/skills/vql-adr-constraints/SKILL.md`:
   - Layer 3 gains the row ADR-018 × ADR-014.
   - Layer 4 gains the row ADR-018 → register ADR-029 §3.
   - Layer 1, "Changing fragment membership": the always column gains ADR-014
     and ADR-023, because the admitted set is derived from domain types and
     holds `SymbolName`s.
   - Layer 1, "Changing the typed pipeline": the also row gains ADR-018 for a
     change to what a function may return or to `domainReturningFunctions`.
   - Layer 2's ADR-018 row, to: `Fragment`, `FragmentViolation`,
     `FragmentCheck.check`, and the admitted functions of `Targeting`.
5. `docs/Architecture.md`, "Fragment membership": `Targeting` takes the
   admitted functions, derived by `TypeCatalog.domainReturningFunctions`.
6. `README.md` line 70: "`Targeting` — no quantifiers, and no function
   applications outside a given set".
7. `CHANGELOG.md`: 0.19.0, "Changed (breaking)" for the five surface changes
   and "Added" for the accessor and `DeclarationSite`. The five are the
   removed case, `Targeting`'s parameter, and the field types of
   `FunctionApplicationNotAllowed`, `UnknownType` and `NameCollision`.
8. `docs/TODOS.md`: T-004 status becomes resolved by this plan, with the
   reason in §4.1. T-012's status paragraph and its "Alternative taken"
   paragraph both say register uses no domain-returning functions, and both
   are brought to the state register ruled.
9. The register task-list entry of §10, in the repository `register`.
10. `build.sbt`: the user runs
    `sed -i 's/^ThisBuild \/ version      := .*/ThisBuild \/ version      := "0.19.0"/' build.sbt`.
    The user runs the release per `docs/RELEASE.md`.

---

## 7. Verification plan

Tests added or changed are listed in §6. The commands that must be green, each
ending in `[success]`:

```bash
sbt "folEngineJVM/testOnly vql.typed.TypeCatalogSpec vql.fragment.FragmentCheckSpec vql.typed.QueryBinderSpec vql.semantics.VagueSemanticsTypedSpec"
sbt test
```

`sbt test` runs both platforms. The end-to-end test of Phase 3 item 2 is the
integration verification `docs/WORKING-INSTRUCTIONS.md` requires for a public
surface change: it reaches the new behaviour through `VagueQueryParser.parse`,
`FolModel`, `VagueSemantics.evaluateTyped` and `VagueSemantics.satisfyingSet`.

The mechanical check of `vql-adr-constraints` must print nothing after
Phase 4:

```sh
for f in docs/ADR-*.md; do grep -q "\`$(basename "$f")\`" .claude/skills/vql-adr-constraints/SKILL.md || echo "MISSING $f"; done
```

---

## File inventory

The heading carries no section number, because the plan gate in `CLAUDE.md`
names the section `## File inventory`.

- `core/src/main/scala/vql/typed/TypeCatalog.scala`
- `core/src/main/scala/vql/fragment/Fragment.scala`
- `core/src/main/scala/vql/fragment/FragmentCheck.scala`
- `core/src/test/scala/vql/typed/TypeCatalogSpec.scala`
- `core/src/test/scala/vql/fragment/FragmentCheckSpec.scala`
- `core/src/test/scala/vql/typed/QueryBinderSpec.scala`
- `core/src/test/scala/vql/semantics/VagueSemanticsTypedSpec.scala`
- `docs/ADR-018.md`
- `docs/ADR-006.md`
- `docs/ADR-014.md`
- `.claude/skills/vql-adr-constraints/SKILL.md`
- `docs/Architecture.md`
- `README.md`
- `CHANGELOG.md`
- `docs/TODOS.md`
- `build.sbt` (the version line, run by the user)

Outside this repository, one file in `register`, written once in Phase 4:
`docs/dev/TODO.md`.
---

## 9. Consumer-document impact

- `README.md` line 70 says `Targeting` means "no quantifiers or function
  applications". Stale; Phase 4 item 6.
- `docs/Architecture.md` lines 124 to 130 say the check "needs no
  `TypeCatalog` or model". It still needs none, and a consumer derives the
  admitted set from one. Stale; Phase 4 item 5.
- `docs/VagueQuantifiers.md` does not mention the fragment or a return-sort
  rule. Not affected.
- Scaladoc: `Fragment`, `Fragment.Targeting`,
  `FragmentViolation.FunctionApplicationNotAllowed`, `FragmentCheck`,
  `TypeCatalogError.UnknownType` and the removed case's doc. Brought to state
  in Phases 1 and 2.

---

## 10. What register must do, named once

Written into `register/docs/dev/TODO.md` as one item in Phase 4. Register's
own plan, `PLAN-RISKTRANSFORM.md` §7.6.12 decision 15, already records the
design; the item lists the call sites this release changes.

- `build.sbt`: `vqlEngineVersion` to `0.19.0`.
- `TargetingPredicate.create` passes `Fragment.Targeting(<set>)` where the set
  is `domainReturningFunctions` of a catalog holding register's function
  signatures. Those signatures live in `RiskTreeKnowledgeBase` in `server`
  today and do not read the tree, so they can be declared where `common` can
  reach them.
- `RiskTreeKnowledgeBase`: `node_id: (NodeIdLiteral) -> Node` and
  `node_named: (NodeNameLiteral) -> Node` as functions, each returning the
  `NodeId` its validator produced; the dispatcher's `functionSymbols` names
  them.
- `FragmentViolation.FunctionApplicationNotAllowed(_)` still matches; the
  field is a `SymbolText` if register ever reads it.
- ADR-029 §3, row `TargetingPredicate.create`: "no function terms" gives way
  as register ruled on 2026-11-13.
