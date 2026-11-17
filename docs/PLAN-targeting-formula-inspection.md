# Implementation Plan: A Consumer Inspects a Targeting Formula Only through the Engine

**Status:** APPROVED, 2026-11-17. Drafted in register's session from a ruling
by the user on 2026-11-17, and elevated in the engine's session the same day.
`TFI-D-1` was ruled A on 2026-11-17 (§3). Every other point is settled by
derivation (§2), and the approval of this document rules them.
**Version this was read at:** 0.22.0, released (tag `v0.22.0`). register pins
0.22.0.
**Target version:** 0.23.0, because `Targeting` gains a parameter and every
construction of it changes. Under early-semver a pre-1.0 breaking change bumps
the minor version.
**Plan code:** `TFI`, the initials of the filename slug
`targeting-formula-inspection`. Decisions are `TFI-D-<n>`.
**Records this plan amends:** `docs/ADR-018.md` §1, §2, §3, its
Alternatives Rejected entry for the list return, and its implementation
table. It creates no record.
**Origin:** register's `docs/dev/plans/PLAN-RISKTRANSFORM.md`, in the
`register` repository, records three rulings this plan serves:

- §8.4-3, ruled 2026-08-10: "**Enforcement locus RULED (user, 2026-08-10):
  engine-side fragment-membership API.** … Register calls parse + membership
  check as one boundary step and keeps treating `Formula` as opaque."
- §8.6.4, ruled 2026-08-14: "`reservedPredicates` now includes `unmitigated`,
  matched case-insensitively, so `unmitigated(x)` / `Mitigated(x)` are rejected
  at authoring … the KB marks its own predicates non-targetable and supersedes
  this hardcoded set."
- §8.4-3, "**Reach RULED (user, 2026-11-17): every inspection of a targeting
  formula.**" register's three walks over `Formula` move behind an engine API,
  and a formula's predicate name "is compared to the forbidden names after
  lower-casing".

---

## 0. Executive summary

**What is there today.** register checks a mitigation's targeting predicate in
`TargetingPredicate.create`, in
`modules/common/src/main/scala/com/risquanter/register/domain/data/TargetingPredicate.scala`
of the `register` repository. It parses the text with `FOLParser.parse` and
calls `FragmentCheck.check(formula, Fragment.Targeting(admitted))`. It then
walks the parse tree itself three more times:

- `atomPredicates` collects every atom's predicate name, so that `mitigate`,
  `mitigated` and `unmitigated` are refused in any case of letters;
- `appliedFunctions` collects every applied function name, nested ones
  included, so that `node_named` is refused;
- `namedNodeLiterals` collects the literal inside every `node_id("…")`, left
  to right with repeats kept. register's browser uses it to show which nodes a
  target names.

Each walk is its own recursion over the nine `Formula` cases, the traversal
`FragmentCheck` already performs.

**What changes.** The engine gains the three things those walks do. Each takes
the consumer's vocabulary as names, as `Targeting` already takes the admitted
function names (ADR-018 §3, ADR-014 §1):

1. The targeting fragment refuses an atom whose predicate is in a set of names
   the consumer gives, compared after lower-casing (R1).
2. `FragmentCheck.violations` reports every violation in pre-order,
   left-to-right traversal. `check` returns its first element (R2).
3. `FragmentCheck.literalArguments` returns the literal argument of every
   application of a named function, left to right with repeats kept (R3).

register then deletes its three walks. §10 states what register does, once.

---

## 1. Requirements

**R1 — a forbidden predicate is refused.** The targeting fragment carries a set
of predicate names a formula may not use. An atom whose predicate name, after
lower-casing, equals a forbidden name after lower-casing, is a violation. The
violation carries the predicate's name as the formula wrote it.

```
forbidden = Set(SymbolName("mitigate"), SymbolName("mitigated"), SymbolName("unmitigated"))
leaf(x) /\ mitigated(x)     // refused: PredicateNotAllowed("mitigated")
leaf(x) /\ Mitigated(x)     // refused: PredicateNotAllowed("Mitigated")
leaf(x) /\ foo(x)           // admitted: an unknown name is not refused here
```

The set lists what is forbidden. It does not list what is admitted. register's
ruling D1 of 2026-08-14 keeps the targeting boundary structural, and "typeless
atoms … are accepted at authoring" there. An unknown predicate is refused later,
by binding.

**R2 — every violation is reported.** `violations` returns every violation in
pre-order, left-to-right traversal. "Every" counts occurrences: two refused
applications of one function are two elements. The walk continues below a
quantifier and into the arguments of a refused application, so nothing inside
either is missed.

```
forall y. leaf(x) /\ mitigated(x) /\ eq(x, node_named("Ops"))
// List(QuantifierNotAllowed,
//      PredicateNotAllowed("mitigated"),
//      FunctionApplicationNotAllowed("node_named"))   (node_named not admitted)
```

`check` returns the first element of `violations`, or `Right(())` when there is
none. For a fragment with no forbidden predicates that is the violation `check`
returns today.

register needs the walk below a quantifier. Its test
`TargetingPredicateSpec`, "accumulates every independent formula-level
violation at once", refuses `forall x . mitigated(y)` with both
`TargetingQuantifierNotAllowed` and `TargetingMitigationStateReferenced`. Its
`appliedFunctions` descends into every application, so `p95(node_named("A"))`
reports both the function and the node name today.

**R3 — the literals of a named function are listed.** Given a formula and a
function name, `literalArguments` returns the literal argument of every
application of that function whose only argument is a literal, left to right
with repeats kept. It descends into the arguments of every other application,
so an application nested inside another is found. It walks below quantifiers.

```
eq(x, node_id("B")) \/ child_of(x, node_id("A")) \/ eq(x, node_id("B"))
// literalArguments(f, SymbolName("node_id")) == List(B, A, B)
leaf(x)
// Nil
eq(x, node_id(y))
// Nil: the argument is a variable
```

register's `TargetingPredicateSpec`, suite `referencedNodes`, pins
`List(other, nodeId, other)` for the same shape, so the order and the repeats
are a contract.

**R4 — both platforms.** register calls all three from code compiled for the JVM
and for the browser. Every addition sits in `vql.fragment`, which both platforms
compile, and uses no JDK method Scala.js lacks (§5.2).

### Not in scope

- A list of admitted predicates. It would refuse `foo(x)` at authoring, which
  register's ruling D1 of 2026-08-14 forbids.
- Any change to the parser. ADR-007 freezes it, and ADR-018 places fragment
  membership over the parser's output for that reason.
- Any knowledge of what a predicate or a function means. The engine receives
  names.
- A sentence for a violation. register reads the case and writes its own
  sentence (§2, point 6).

---

## 2. Settled by derivation

1. **Every violation is a second entry beside `check`, and `check` is its
   first element.** ADR-018's context records the path: "reporting every
   offending node is a strictly wider return type that can be added later
   without breaking the narrower one". Its Alternatives Rejected entry for
   `Either[List[FragmentViolation], Unit]` ends: "Widening to a list later is
   strictly additive, so it is not carried now." So `check` keeps its
   signature, and `violations` is added. `check` is defined from `violations`,
   so the two cannot disagree.
2. **"Every" counts occurrences.** ADR-018 says "every offending node", and a
   node is an occurrence. A consumer that wants each kind once calls
   `distinct`.
3. **The walk continues below a quantifier and into a refused application's
   arguments.** R2 shows register's two tests that need it. Pre-order puts the
   quantifier or the refused application before what lies inside it, so the
   first element is unchanged.
4. **Both sides are lower-cased.** The ruling says the formula's name "is
   compared to the forbidden names after lower-casing". Lower-casing the
   forbidden names too changes the result only for a consumer that writes a
   forbidden name with a capital letter. Under the literal reading, such a name
   would never match anything. This is my choice, and it is reversible.
   Lower-casing uses `Character.toLowerCase` on each character, which ignores
   the default locale. `String.toLowerCase()` uses it, and under a Turkish
   locale maps `I` to `ı`, so `MITIGATED` would not match.
5. **`literalArguments` sits in `FragmentCheck`.** It walks the same tree, and
   the two walks share the private recursion over the formula's atoms. A new
   object would need a name the project has no term for. This is reversible.
6. **`FragmentViolation` gains no `message` and stays in `vql.fragment`.**
   ADR-018's ADR-012 cross-reference calls `check`'s result
   "membership-with-reason, not an operational-failure channel", so it is a
   check result, not an error that ADR-023 §2 places in `vql.error`. The
   `PLAN-predicate-satisfying-set.md` plan places `SatisfyingCarriersViolation`
   beside its check by the same reasoning. No caller would read a `message`:
   register writes its own sentence. A method with no caller is not added.
7. **The new case is `PredicateNotAllowed(predicate: SymbolText)`.** It follows
   `FunctionApplicationNotAllowed(function: SymbolText)`: the rule broken,
   `NotAllowed`, and the symbol as the formula wrote it, as a bounded text type
   (ADR-022 §2). It stores no sentence (ADR-023, code smell "A stored sentence
   beside the fields").
8. **A literal is returned as `LiteralText`**, the lexer's type for a literal's
   content (ADR-022 §2). Every `Term.Const` the parser builds holds a quoted
   literal's content, a numeral, a decimal or `nil` (`TermParser.scala`,
   `parseAtomicTerm` and `isConstName`). All of these are within the literal
   alphabet, so `LiteralText(literal)` holds for every parsed formula.
   `QueryBinder` builds `LiteralText(name)` from a `Term.Const` the same way. A
   hand-built `Term.Const` outside the alphabet throws
   `IllegalArgumentException`, which is a defect in the caller (ADR-012).

---

## 3. Ruled decision

### TFI-D-1 — where the forbidden predicates go

**Ruled (user, 2026-11-17): A**, a second parameter on `Targeting`. The options
as they were presented are kept below as the record of the ruling.

**Why it matters now.** The forbidden set has to reach the targeting walk. Where
it is declared decides whether 0.23.0 breaks the one construction of
`Targeting` that register has, and whether one fragment still describes one
sub-language.

**The moving parts.** `Fragment` is an `enum` whose cases each describe one
sub-language of FOL. `Targeting(admittedFunctions)` is the language of
register's targets, and `Screening(k)` bounds quantifier nesting. A consumer
passes one `Fragment` to `check` and gets one answer. register builds
`Targeting` in one place, `TargetingPredicate.checkFragment`.

**Option A — a second parameter on `Targeting`.**

```scala
case Targeting(admittedFunctions: Set[SymbolName], forbiddenPredicates: Set[SymbolName])
```

- *Example:* register writes
  `FragmentCheck.violations(formula, Fragment.Targeting(admitted, forbidden))`
  and gets the quantifier, the forbidden predicate and the refused functions in
  one list, in tree order.
- *Pro:* the targeting language stays one value, and one walk applies all its
  rules.
- *Con:* breaking. Every construction of `Targeting` changes, which is register's
  one call site and the engine's tests. The release is 0.23.0.

**Option B — a third `Fragment` case for the predicate rule.**

```scala
case PredicatesExcluded(forbiddenPredicates: Set[SymbolName])
```

- *Example:* register calls `violations` twice, once with `Targeting(admitted)`
  and once with `PredicatesExcluded(forbidden)`, and concatenates the lists.
  For `forall y. mitigated(y) /\ f(y)` it gets the quantifier and `f` from the
  first call, and `mitigated` from the second. The combined list is no longer
  in tree order.
- *Pro:* only adds. The release is 0.22.1, and register's existing call compiles
  unchanged.
- *Con:* one language is split across two fragments. A consumer that checks only
  `Targeting` silently admits the forbidden predicates.

A third shape, a default `forbiddenPredicates: Set[SymbolName] = Set.empty` on
`Targeting`, changes the case class's constructor and `unapply` just as A
does, so it is still 0.23.0. It also makes omitting the rule free. It is
therefore not presented as an option.

**My recommendation: A.** It keeps one value per sub-language. Its cost is one
line in register, which §10 changes anyway because the admitted set loses
`node_named`. This holds as long as no consumer other than register builds
`Targeting`. A search of register finds the one call site, and the engine has no
other consumer.

---

## 4. ADR compliance review (planning phase)

| Record | Verdict | Basis |
|---|---|---|
| ADR-001 (many-sorted binding) | Not relevant | No catalog, binder or model changes. |
| ADR-002 (parser combinators) | Not relevant | No parser changes. |
| ADR-003 (HDR sampling) | Not relevant | No sampling changes. |
| ADR-004 (layering) | Relevant, compliant | `vql.fragment` gains an import of `lexer.LiteralText`, from the foundation below it. It still imports no `vql.typed`. `FragmentCheck` stays an operations object over inert data. |
| ADR-006 (enum encoding) | Relevant, compliant | `Fragment` and `FragmentViolation` stay pure-data `enum`s. The new case and parameter carry data only. |
| ADR-007 (ported core) | Relevant, compliant | No file in its scope tables changes. The walks read the parser's output. |
| ADR-012 (error channel) | Relevant, compliant | `violations` returns a membership result, as `check` does. `LiteralText`'s `require` guards what a value is, and the parser never trips it (§2, point 8). |
| ADR-014 (quantifiability) | Relevant, compliant | The admitted functions are still a name set a consumer computes from `domainReturningFunctions`. The forbidden predicates are a name set too. Neither holds a sort. |
| ADR-015 (value boundaries) | Not relevant | No `Value` and no literal validator is touched. `literalArguments` returns text, never a carrier. |
| ADR-016 (carrier witness) | Proposed, not validated against | No literal payload changes. |
| ADR-017 (formula ranges) | Not relevant | No range or satisfying-set code changes. |
| ADR-018 (fragment membership) | Relevant, **amended** | §1's code block gains the parameter and the case. §2 gains `violations` and defines `check` from it. §3 gains the forbidden-predicate rule and states that `violations` walks below a quantifier and into a refused application. The Alternatives Rejected entry for the list return is marked where it sits as taken up by `violations`. The implementation table gains `violations` and `literalArguments`. |
| ADR-019 | Superseded by ADR-023 | Not validated against. |
| ADR-020 (error accumulation) | Not relevant | The binder is not touched. |
| ADR-021 | Superseded by ADR-023 | Not validated against. |
| ADR-022 (typed refusals) | Relevant, compliant | `PredicateNotAllowed` holds a `SymbolText` and `literalArguments` returns `LiteralText`, both bounded text types (§2). No boundary converts a new failure. |
| ADR-023 (errors in `vql.error`) | Relevant, compliant | `FragmentViolation` is a check result, not an error (§2, point 6). The names are `SymbolName` from `vql.id`. |
| ADR-024 (parse depth bound) | Relevant, compliant | `violations` and `literalArguments` are new recursions over the tree. Phase 1 measures both at the nesting limits on a 1 MiB JVM thread, in the existing stack test, and on Node (§7). |
| ADR-025 (order-independent evaluation) | Not relevant | No evaluation or domain code changes. |
| ADR-00X (record template) | Relevant, compliant | ADR-018's amendment keeps its structure. |

**Layer 3 of `vql-adr-constraints`.** The row ADR-018 × ADR-014 still holds:
the admitted functions come from a rule on sorts. The row ADR-012 × ADR-018
still holds: `violations`, like `check`, returns a membership result.

**Layer 4 of `vql-adr-constraints`.** The row "ADR-018 §3 → register ADR-029 §3,
row `TargetingPredicate.create`" applies, because the plan changes what
`Targeting` admits. register's row reads "restricted to the targeting fragment
(no quantifiers, and no function term other than a node-returning function), a
single free variable, and no mitigation-state predicate and no node name". The
refusals it lists are unchanged after register adopts the release, so the
register record stays true. The engine's own Layer 4 row quotes an older
register wording, "no quantifiers, no function terms". Phase 2 brings that row
to register's current text and adds the forbidden predicates. The row "ADR-022
§2, §3 → register ADR-035 §5" applies to the new case, which holds caller text
as `SymbolText`. register maps every violation to a refusal that carries no
text (`fragmentProblem` in `TargetingPredicate.scala`), so ADR-035 §5 stays true
as long as register keeps doing so.

**Amending the corpus.** No record is added, retired, or changes status. ADR-018's
Layer 2 row, "Structural fragment membership over the parse tree", stays true.
The check in `vql-adr-constraints`, "Keeping this document correct", must still
print nothing (§7).

---

## 5. Target behaviour

### 5.1 The fragment — `core/src/main/scala/vql/fragment/Fragment.scala`

```scala
enum Fragment:
  /**
   * No quantifier nodes, no function application outside `admittedFunctions`, and no atom whose
   * predicate is in `forbiddenPredicates`, compared after lower-casing both names. Admits variables
   * and inline literals (`Term.Const`). An admitted application's arguments are walked by the same
   * rule, so a refused function inside an admitted one is still refused. `Targeting(Set.empty,
   * Set.empty)` admits no function at all, arithmetic operators included, and forbids no predicate.
   */
  case Targeting(admittedFunctions: Set[SymbolName], forbiddenPredicates: Set[SymbolName])

  case Screening(maxQuantifierDepth: Int)    // unchanged

enum FragmentViolation:
  case QuantifierNotAllowed                                  // unchanged
  case FunctionApplicationNotAllowed(function: SymbolText)   // unchanged

  /** Targeting: an atom's predicate is forbidden; `predicate` is its name as the formula wrote it. */
  case PredicateNotAllowed(predicate: SymbolText)

  case QuantifierDepthExceeded(limit: Int, found: Int)       // unchanged
```

`FragmentViolation`'s scaladoc changes from "carrying the first violation" to
saying that `check` carries the first violation and `violations` carries all of
them, in pre-order, left-to-right traversal.

**Family.** `PredicateNotAllowed` joins `FragmentViolation`, the check-result
family in `core/src/main/scala/vql/fragment/Fragment.scala`, whose member
`case FunctionApplicationNotAllowed(function: SymbolText)` it follows. The
nearest existing case, `TypeCheckError.UnknownPredicate(name: SymbolText)`,
denotes a name the catalog does not declare, which is a different concept: a
forbidden predicate may be declared. `forbiddenPredicates` follows
`admittedFunctions: Set[SymbolName]`, its neighbour in the same case.

**Raw primitives.** None is new. `limit` and `found` stay `Int`.

### 5.2 The check — `core/src/main/scala/vql/fragment/FragmentCheck.scala`

```scala
import lexer.{LiteralText, SymbolText}

object FragmentCheck:

  /** `Right(())` iff `formula` lies in `fragment`; `Left` carries the first element of `violations`. */
  def check(formula: Formula[FOL], fragment: Fragment): Either[FragmentViolation, Unit] =
    violations(formula, fragment) match
      case Nil        => Right(())
      case first :: _ => Left(first)

  /**
   * Every rule `formula` breaks in `fragment`, in pre-order, left-to-right traversal, one element per
   * occurrence. Empty iff `formula` lies in `fragment`. The targeting walk continues below a
   * quantifier and into a refused application's arguments.
   * {{{
   * // Targeting(Set(SymbolName("node_id")), Set(SymbolName("mitigated")))
   * forall y. Mitigated(y) /\ p95(node_named(y))
   * // List(QuantifierNotAllowed, PredicateNotAllowed(Mitigated),
   * //      FunctionApplicationNotAllowed(p95), FunctionApplicationNotAllowed(node_named))
   * }}}
   */
  def violations(formula: Formula[FOL], fragment: Fragment): List[FragmentViolation]

  /**
   * The literal argument of every application of `function` whose only argument is a literal, left
   * to right, repeats kept, below quantifiers and inside other applications.
   * {{{
   * eq(x, node_id("B")) \/ child_of(x, node_id("A")) \/ eq(x, node_id("B"))
   * // literalArguments(f, SymbolName("node_id")) == List(B, A, B)
   * }}}
   */
  def literalArguments(formula: Formula[FOL], function: SymbolName): List[LiteralText]
```

The bodies:

```scala
  def violations(formula: Formula[FOL], fragment: Fragment): List[FragmentViolation] =
    fragment match
      case Targeting(admitted, forbidden) => targetingViolations(formula, admitted, forbidden)
      case Screening(k)                   =>
        val found = quantifierDepth(formula)
        if found <= k then Nil else List(QuantifierDepthExceeded(k, found))

  private def targetingViolations(
    formula: Formula[FOL],
    admitted: Set[SymbolName],
    forbidden: Set[SymbolName],
  ): List[FragmentViolation] =
    formula match
      case Formula.False | Formula.True      => Nil
      case Formula.Atom(FOL(predicate, terms)) =>
        predicateViolation(predicate, forbidden) ++ terms.flatMap(termViolations(_, admitted))
      case Formula.Not(p)                    => targetingViolations(p, admitted, forbidden)
      case Formula.And(p, q)                 =>
        targetingViolations(p, admitted, forbidden) ++ targetingViolations(q, admitted, forbidden)
      case Formula.Or(p, q)                  => /* as And */
      case Formula.Imp(p, q)                 => /* as And */
      case Formula.Iff(p, q)                 => /* as And */
      case Formula.Forall(_, p)              => QuantifierNotAllowed :: targetingViolations(p, admitted, forbidden)
      case Formula.Exists(_, p)              => QuantifierNotAllowed :: targetingViolations(p, admitted, forbidden)

  private def predicateViolation(predicate: String, forbidden: Set[SymbolName]): List[FragmentViolation] =
    Option.when(forbidden.exists(name => lowerCase(name.value) == lowerCase(predicate)))(
      PredicateNotAllowed(SymbolText.of(predicate))
    ).toList

  /** `s` with each character lower-cased by `Character.toLowerCase`, which ignores the default locale. */
  private def lowerCase(s: String): String = s.map(Character.toLowerCase)

  private def termViolations(term: Term, admitted: Set[SymbolName]): List[FragmentViolation] =
    term match
      case Term.Var(_) | Term.Const(_)                                => Nil
      case Term.Fn(name, args) if admitted.contains(SymbolName(name)) =>
        args.flatMap(termViolations(_, admitted))
      case Term.Fn(name, args)                                        =>
        FunctionApplicationNotAllowed(SymbolText.of(name)) :: args.flatMap(termViolations(_, admitted))

  def literalArguments(formula: Formula[FOL], function: SymbolName): List[LiteralText] =
    atomTerms(formula).flatMap(literalsIn(_, function))

  /** The terms of every atom in `formula`, left to right, below quantifiers too. */
  private def atomTerms(formula: Formula[FOL]): List[Term] =
    formula match
      case Formula.False | Formula.True             => Nil
      case Formula.Atom(FOL(_, terms))              => terms
      case Formula.Not(p)                           => atomTerms(p)
      case Formula.And(p, q)                        => atomTerms(p) ++ atomTerms(q)
      case Formula.Or(p, q)                         => atomTerms(p) ++ atomTerms(q)
      case Formula.Imp(p, q)                        => atomTerms(p) ++ atomTerms(q)
      case Formula.Iff(p, q)                        => atomTerms(p) ++ atomTerms(q)
      case Formula.Forall(_, p)                     => atomTerms(p)
      case Formula.Exists(_, p)                     => atomTerms(p)

  private def literalsIn(term: Term, function: SymbolName): List[LiteralText] =
    term match
      case Term.Fn(name, Term.Const(literal) :: Nil) if name == function.value => List(LiteralText(literal))
      case Term.Fn(_, args)                                                   => args.flatMap(literalsIn(_, function))
      case Term.Var(_) | Term.Const(_)                                        => Nil
```

`checkTargeting`, `firstFnViolation` and `firstFn` are removed, because
`violations` replaces them. `quantifierDepth` is unchanged.

`check` now walks the whole formula before it answers, where today it stops at
the first violation. ADR-024 bounds the tree at 64 parentheses and 256 operators
and quantified variables, so the full walk is bounded too.

`Character.toLowerCase(Char)` and `String.map` are implemented by Scala.js. The
test suite runs every test in `core/src/test` on Node, which checks this (§7).

**Family.** `violations` and `literalArguments` join `FragmentCheck`, the
operations object in `core/src/main/scala/vql/fragment/FragmentCheck.scala`,
whose member `def check(formula: Formula[FOL], fragment: Fragment)` they sit
beside. No existing function lists every violation or a function's literal
arguments. `QueryBinder` resolves a `Term.Const` against the catalog, but it
lists nothing.

**Raw primitives.** `predicate: String` in `predicateViolation` is
`FOL.pred`, which the ported foundation keeps a `String` (ADR-007 C8), and it
becomes a `SymbolText` before it leaves. The private `lowerCase` works on text
and returns text.

---

## 6. Implementation phases

### Phase 1 — the engine

1. Edit `Fragment.scala` as §5.1 states.
2. Edit `FragmentCheck.scala` as §5.2 states. Its object scaladoc says it
   decides membership and lists a function's literal arguments.
3. Edit `core/src/test/scala/vql/fragment/FragmentCheckSpec.scala`:
   - every `Fragment.Targeting(admitted)` becomes
     `Fragment.Targeting(admitted, Set.empty)`. No assertion
     changes;
   - the tests §7 lists are added.
4. Edit `core/.jvm/src/test/scala/parser/NestingBoundStackSpec.scala`: the
   1 MiB test's `Fragment.Targeting(Set(SymbolName("f")))` gains its second
   argument, and the test also calls `violations` and
   `literalArguments` on the same formula.
5. Run `sbt test`.

### Phase 2 — the records and the documents

1. `docs/ADR-018.md`, as §4's verdict row states. The Alternatives Rejected
   entry `Either[List[FragmentViolation], Unit]` gains the sentence: "The list
   is returned by `violations`, beside `check`."
2. `docs/Architecture.md`, from line 125: `Targeting(admitted, forbidden)`,
   and `violations` and `literalArguments` beside `check`.
3. `README.md`, line 70: the same.
4. `CHANGELOG.md`, the 0.23.0 section: the changed `Targeting`, the new
   `PredicateNotAllowed`, `violations` and `literalArguments`, and that `check`
   now reports a forbidden predicate.
5. `.claude/skills/vql-adr-constraints/SKILL.md`, Layer 4, row "ADR-018 §3": it
   quotes register's current ADR-029 §3 text (§4) and names the forbidden
   predicates.
6. `build.sbt`, the version line, run by the user.

---

## 7. Verification plan

**Tests added to `core/src/test/scala/vql/fragment/FragmentCheckSpec.scala`**,
each built from `FOLParser.parse`, which is the entry path a consumer uses:

1. R1: `leaf(x) /\ mitigated(x)` and `leaf(x) /\ Mitigated(x)` each give
   `List(PredicateNotAllowed(…))` with the name as written. `leaf(x) /\ foo(x)`
   gives `Nil`. A forbidden name inside `~` and on each side of `==>` is found.
   A forbidden set written `SymbolName("Mitigated")` refuses `mitigated(x)`
   (§2, point 4).
2. R2: R2's example gives exactly the list shown, in that order.
   `p95(node_named("A"))` with neither admitted gives both functions, outer
   first. `leaf(x)` gives `Nil`. For each formula in the existing targeting
   tests, `check` returns `violations`' first element.
3. R3: R3's three examples. `node_id(node_id("A"))` gives `List(A)`, from the
   inner application. A formula with `node_id("A")` below a quantifier gives
   `List(A)`.
4. Screening: `violations` gives `List(QuantifierDepthExceeded(k, found))` or
   `Nil`, and `check` agrees.

The end-to-end path for these entries is parse, then check. Binding and
evaluation do not apply, because neither entry takes a catalog or a model, as
ADR-018 §5 states for `check`.

**Commands, run from the repository root:**

| Command | Expected |
|---|---|
| `sbt test` | pass, on the JVM and on Node |
| `grep -rn "checkTargeting\|firstFnViolation" core/src/main/scala` | no output |
| `grep -rn "import vql.typed" core/src/main/scala/vql/fragment` | no output |
| `for f in docs/ADR-*.md; do grep -q "\`$(basename "$f")\`" .claude/skills/vql-adr-constraints/SKILL.md \|\| echo "MISSING $f"; done` | no output |

---

## File inventory

The inventory is `docs/PLAN-targeting-formula-inspection-INVENTORY.md`, which
the user writes by running `.claude/bin/approve-inventory`. One file the work
edits is not in it: `.claude/skills/vql-adr-constraints/SKILL.md`, because the
approval script lists no `.claude/` path. That file is outside the gated
region. The version line in `build.sbt` is changed by the user.

---

## 9. Consumer-document impact

- `README.md` line 70 describes `Targeting(admitted)` and says `check` returns
  "the first `FragmentViolation`". It changes in Phase 2.
- `docs/Architecture.md` lines 125 to 127 say the same. They change in Phase 2.
- `docs/VagueQuantifiers.md` names neither.
- Scaladoc: `Fragment.Targeting`, `FragmentViolation` and `FragmentCheck` change
  with the code (§5). `TypeCatalog.domainReturningFunctions` says a consumer
  "passes this set to `Fragment.Targeting`", which stays true.

---

## 10. What register must do, named once

register adopts the release as a slice of `docs/dev/plans/PLAN-RISKTRANSFORM.md`,
in the `register` repository. §8.4-3's ruling of 2026-11-17 already points
there at this plan, so no entry is added to register's task list.

1. Each case of `QueryPredicate`, in
   `modules/common/src/main/scala/com/risquanter/register/common/FolSymbols.scala`,
   says whether a targeting predicate may use it. The forbidden set is derived
   from those cases as `Set[SymbolName]`, and `reservedPredicates` in
   `TargetingPredicate.scala` is deleted. The forbidden cases are `Mitigate`,
   `Mitigated` and `Unmitigated`, as today.
2. The admitted set passed to `Targeting` is `Signatures.targetingFunctions`
   without `node_named`, so the engine refuses `node_named` as a function that
   is not admitted.
3. `atomPredicates`, `appliedFunctions`, `termFunctions`, `namedNodeLiterals` and
   `termNodeLiterals` are deleted. `checkFragment` calls `violations` and maps
   each element to register's own refusal:
   - `PredicateNotAllowed` becomes `TargetingMitigationStateReferenced`;
   - `FunctionApplicationNotAllowed` naming `node_named` becomes
     `TargetingNodeNameReferenced`;
   - any other `FunctionApplicationNotAllowed` becomes
     `TargetingFunctionNotAllowed`;
   - `QuantifierNotAllowed` becomes `TargetingQuantifierNotAllowed`.
   Each refusal is reported once, so the mapped list is made distinct.
4. `referencedNodes` calls `literalArguments(formula, SymbolName("node_id"))`
   and keeps the literals that parse as a `NodeId`.

**One behaviour widens.** Today `checkFragment` reports only the first fragment
violation. A formula with both a quantifier and a function that is not admitted
is refused with `TargetingQuantifierNotAllowed` alone. After adoption it is
refused with both. This is what register's §7.6.15 asks for: "a predicate
breaking two rules reports both". Every other refusal, and the nodes the Analyze
view lists and their order, stay as they are.
