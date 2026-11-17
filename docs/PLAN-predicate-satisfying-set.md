# Implementation Plan: A Knowledge Base Answers a Predicate's Whole Satisfying Set

**Status:** PROPOSED, 2026-11-17. Every decision is settled by derivation (§2).
Approval of this document rules them. Each one is reversible.
**Version this was read at:** 0.21.0.
**Target version:** 0.22.0. Under early-semver a pre-1.0 breaking change bumps
the minor version. `MapDispatcher` is a case class, and it gains a field. Its
constructor and its `unapply` change, so a consumer compiled against 0.21.0
must recompile. Every result is unchanged, except one: the refusal reported
for a sampled scope, which §2 settled point 8 fixes.
**Plan code:** `PSS`, the initials of the filename slug
`predicate-satisfying-set`. Decisions are `PSS-D-<n>`.
**Origin:** `docs/TODOS.md` T-016.
**Consumer plan:** in the repository `register`,
`docs/dev/plans/PLAN-TARGETING-SET-EVALUATION.md`. That plan waits for the
release carrying this one, and quotes §5.1's signature.
**Records this plan creates and amends:** it creates `docs/ADR-026.md`. It
amends `docs/ADR-017.md` §6, `docs/ADR-025.md` §5 and the implementation table
of `docs/ADR-015.md`. Why a new record is needed is §2, settled point 9.

---

## 0. Executive summary

**What is slow today.** `TypedSemantics.satisfyingSet` answers the question
"which elements of this sort satisfy this formula". It visits every element of
the sort and evaluates the whole formula there. Each atom, one predicate
application such as `leaf(x)`, costs one call to the dispatcher's
`evalPredicate` at each element. Range extraction in `evaluate` works the same
way. The cost is the number of elements times the number of atoms.

**What changes.**

1. **The dispatcher may answer an atom as a set.** `RuntimeDispatcher` gains
   one method with a default body. It is given a predicate and every argument
   except the one in the variable's position. It answers with the carriers of
   every element that satisfies the predicate, or it declines.
2. **The engine evaluates a formula over a set of elements.** Each subformula
   is evaluated over a set of candidate elements. The result is split three
   ways: the elements where the subformula is true, the elements where it is
   unknown (each with the refusal that made it unknown), and every other
   candidate, where it is false. `∧` evaluates its right operand only over the
   left operand's non-false elements. That is exactly where per-element
   evaluation evaluates it.
3. **An atom the dispatcher answers costs one call.** An atom it declines, and
   every quantified subformula, is evaluated per element, as today, but only
   over its candidates.

**What stays the same.** The result is the same set, and the same refusal, as
per-element evaluation gives, given the dispatcher keeps the law of §4.1. No
signature of `TypedSemantics`, `VagueSemantics` or `RuntimeModel` changes. A
dispatcher that does not override the new method gives today's results at
today's cost.

---

## 1. Requirements

1. **Equal results.** For every formula, every model whose dispatcher keeps
   the law of §4.1, and every candidate set, the three sites return what
   per-element evaluation returns:
   - `satisfyingSet` returns the same `Right` set, or the same refusal;
   - range extraction returns the same elements in the same domain order;
   - scope evaluation over a sample returns the same elements.
2. **Cost follows the answers.** A formula whose atoms the dispatcher answers
   makes no `evalPredicate` call. It makes one `satisfyingCarriers` call per
   atom, and one `evalFunction` call per fixed function argument. Its set
   operations cost the sizes of the answered sets, plus the size of the
   domain at each `~` and `→`, whose result can hold most of the domain.
3. **Declining costs one call per fixed function.** A dispatcher that does
   not override `satisfyingCarriers` gets the same `evalPredicate` calls, at
   the same elements, as per-element evaluation makes. It gets one extra
   `evalFunction` call for each fixed function argument of each atom. That is
   the call that produced the arguments the dispatcher was asked about.
4. **The refusal follows domain order.** When several elements are unknown,
   the refusal reported is the one at the element first in domain order. At
   that element it is the refusal per-element evaluation meets first.
5. **The law is checkable.** A consumer can check its dispatcher's answer for
   any atom against its own `evalPredicate`, with a function the engine ships.

### Not in scope

- **A failure that is not a refusal.** Such a failure (`UnboundVariableError`,
  `DomainNotFoundError`, `VariableSortMismatch`) passes up at once, as under
  ADR-025 §2. Per-element evaluation stops at the first element whose formula
  fails, so it never sees a failure at a later element. The set evaluation
  visits every candidate. Where one element refuses and a later one fails
  otherwise, the set evaluation reports the later failure. A bound formula
  never fails otherwise: the binder rules out every such case. Requirement 1
  therefore covers every formula that reaches evaluation through
  `QueryBinder`.
- **The query language, parsing, binding and what a refusal says** do not
  change.

---

## 2. Decisions

None is open. Each point below was an open decision in the draft, labelled
`PSS-D-<n>`, or was found while elevating it. Each is settled by derivation
from the code and the records.

### Settled by derivation

1. **PSS-D-1: the question is a method with a default body on
   `RuntimeDispatcher`.** The draft weighed this against a second trait that a
   dispatcher may also extend.
   - The engine and register hold 16 anonymous `new RuntimeDispatcher`
     implementations. A method with a body keeps every one of them compiling.
   - A second trait needs a runtime type test on the dispatcher at every atom.
     `MapDispatcher`, the dispatcher register uses, would have to extend it
     anyway, so every `MapDispatcher` would be one.
   - The default body declines, so a dispatcher that does not override it
     behaves as it does today.

2. **PSS-D-1, inside it: the open position is the gap between two lists.**
   The draft's `open: Int` beside the fixed arguments is a raw primitive.
   Plan-gate item 1 asks for a type or a reason. The signature takes `before`
   and `after`, the arguments on either side of the variable's position. The
   position is `before.size`, and there is exactly one open position by
   construction. No index can fall outside the argument list.

3. **The answer is carriers, and the engine wraps them.** ADR-015's smell
   "Constructing `Value` directly in a function lambda" has `evalFunction`
   return the carrier and the framework wrap it. The set answer follows the
   same rule. `satisfyingCarriers` returns `Option[Set[Any]]`, and the engine
   builds each `Value` with the variable's sort. `Any` is the carrier's erased
   type, as in `evalFunction`'s `Either[String, Any]`.

4. **PSS-D-2: the fallback reaches per subformula, three-valued.** The draft's
   option (b) evaluated `q` over `p`'s set. With a two-valued set that is wrong
   under ADR-025. Where `p` is unknown at an element and `q` is false there,
   `p ∧ q` is false, not unknown. A two-valued set for `p` either drops that
   element or reports a refusal Kleene evaluation does not report. So each
   subformula's result holds the true elements and the unknown elements with
   their refusals. Every other candidate is false (§4.3). The draft's option
   (a), all or nothing, is the special case where no atom is answered. It
   gives up the gain for every formula with one declined atom, for no gain in
   simplicity: (b) has the same rules, and needs no separate pass.

5. **Sets are listed or complemented.** `~p` over the whole domain is the
   domain minus `p`'s set. Held as a listed set, that costs the domain's size
   at every `~`. So a set is either listed, or "every element but these"
   (§5.2). Every operation then costs the sizes of the listed parts.

6. **PSS-D-3: all three sites use the set evaluation.** Range extraction,
   scope evaluation over a sample and `satisfyingSet` each evaluate a formula
   at every element of a set. One evaluator for all three keeps one
   implementation of that question. Register's query pane calls
   `evaluateTyped`, so range extraction is on its path.

7. **A fixed argument is evaluated once.** An atom is answered only when one
   argument is the variable itself and no other argument mentions it. The
   other arguments are then the same at every element. Register's
   `descendant_of(x, node_id("IT"))` calls `node_id` once in place of once per
   node. This relies on the dispatcher computing the same answer for the same
   arguments, which the law states (§4.1). A fixed argument that refuses
   makes the atom unknown at every candidate, with that refusal, as
   per-element evaluation gives.

8. **The sampled scope's refusal follows domain order.** This defect is in
   0.21.0 and was found while elevating this plan. `collectSatisfyingSample`
   folds the sample, a `Set[Value]`, in its iteration order. When several
   sampled elements refuse, the one reported follows the `Set`'s hash order.
   ADR-025 §3 states "When several refusals are met, the first in evaluation
   order is returned", and §5 that scope evaluation walks the domain "in its
   declared order". The set evaluation settles every site by domain position
   (§5.3, `settle`), which fixes it.

9. **A new record, ADR-026, holds the law and the evaluation rules.** The law
   binds every consumer that overrides `satisfyingCarriers`. The rules bind
   future evaluation code: a new connective needs its rule over a three-way
   split, and the result must equal per-element Kleene evaluation. ADR-025
   states the per-element semantics, and ADR-017 the satisfying-set entry.
   Neither states a contract for a dispatcher that answers sets.

10. **PSS-D-4: the law check ships in the published artifact.** The law is
    stated in engine terms, and the engine can compute both sides of it. A
    consumer-written check repeats the per-element filter in every consumer.
    `SatisfyingCarriersLaw.check` (§5.4) is main code and depends on no test
    framework.

11. **PSS-D-5: the version is 0.22.0.** `CLAUDE.md`'s table: "A public
    signature removed or changed … MINOR". `MapDispatcher`'s constructor
    gains a parameter, which changes its `apply`, `copy` and `unapply`.

12. **`MapDispatcher` answers sets from a map, as it answers predicates.**
    Register builds its dispatcher as a `MapDispatcher`, a case class that
    cannot be extended. It gains `satisfyingSets`, keyed by predicate, with
    `Map.empty` as its default, as `functions` has. A key with no entry in
    `predicates` is refused by `require`. An answer for a predicate the
    dispatcher cannot evaluate breaks the law at every element.

---

## 3. ADR compliance review (planning phase)

| ADR | Verdict | Basis |
|---|---|---|
| ADR-001 (many-sorted binding) | Relevant, compliant | `RuntimeModel.validateAgainst` and `FolModel` keep every check. No entry point changes. The set evaluation reads only bound formulas. |
| ADR-002 (parser-combinator style) | Not relevant | No parser is touched. |
| ADR-003 (HDR sampling) | Relevant, compliant | The sample is drawn as today, from the range in domain order. Scope evaluation over it returns the same elements. Only the refusal choice changes, to domain order (§2, settled point 8). |
| ADR-004 (layering) | Relevant, compliant | `ElementSet` and `SatisfyingCarriersLaw` live in `vql.typed` and import `vql.error` and `vql.id` only. `ElementSet`'s operations live in its companion object, beside data with no methods (§1). |
| ADR-006 (enum vs sealed trait) | Relevant, compliant | `ElementSet` and `LawViolation` are pure-data `enum`s. `Partition` and `DomainView` are case classes. New files follow §4 and §5. |
| ADR-007 (OCaml-ported core) | Not relevant | No file in its scope tables is touched. |
| ADR-012 (error channel) | Relevant, compliant | `MapDispatcher`'s key check is a `require` on what the dispatcher is. Evaluation keeps its `Either` channel. `SatisfyingCarriersLaw.check` returns its finding as an `Either`, as `FragmentCheck.check` does (ADR-018's cross-ADR section). |
| ADR-014 (quantifiability) | Relevant, compliant | The set evaluation runs on the root variable's domain, which the binder and `satisfyingSet`'s replicated check already made a domain type. Inner quantifiers are evaluated per element through `quantify`, unchanged. |
| ADR-015 (value boundaries) | Relevant, **amended** | `satisfyingCarriers` returns carriers, and the engine wraps them, as with `evalFunction` (§2, settled point 3). Dispatchers keep reading `args(i).extract[A]`. No cast is added. The implementation table's `RuntimeModel.scala` row gains `satisfyingCarriers`. |
| ADR-016 (carrier witness) | Not relevant, Proposed | `LiteralRef.value` is not touched. |
| ADR-017 (formula ranges) | Relevant, **amended** | §1's definition of `D_R` and §2's exhaustive extraction hold. An answered set is the whole answer over the domain, and nothing is sampled. §6's bullet "Enumerates `model.domains(sort(v))` and filters with the existing `evalFormula`" describes the mechanism that changes. Phase 3 brings it to ADR-026 and updates the implementation table. |
| ADR-018 (fragment membership) | Not relevant | `FragmentCheck` reads the parse tree, not a model. |
| ADR-020 (binder error accumulation) | Not relevant | The binder is not touched. |
| ADR-022 (typed refusals) | Relevant, compliant | No refusal case is added or reshaped. `PredicateRefused` and `FunctionRefused` reach the caller as today. `LawViolation.Refused` holds the dispatcher's own text, as `PredicateRefused.dispatcherMessage` does, and is a check result, never a refusal of the caller's query. |
| ADR-023 (identifiers in `vql.id`) | Relevant, compliant | No error type is added. `LawViolation` is a check result that holds `Value`s, so it cannot sit in `vql.error`, which does not import `vql.typed`. It sits beside the check, as `FragmentViolation` sits in `vql.fragment`. |
| ADR-024 (parse depth bound) | Relevant, compliant | §5 asks that a new recursive walk of the tree be measured against both limits. `partition` is one. Phase 3 item 2 measures it on a 1 MiB interpreted JVM thread and on Node. |
| ADR-025 (order-independent evaluation) | Relevant, **amended** | §1 to §3 hold. The set evaluation gives Kleene's value at every element (§4.3), and refusals are chosen by domain order. §5's sentence "Range extraction, scope evaluation, `satisfyingSet` and every quantifier walk a domain in its declared order" describes per-element walking. Phase 3 brings it to the set evaluation. |

**Deviations detected:** none.

**What the review read.** Every record in force was read on 2026-11-17, and
ADR-016 (Proposed). The activities of `vql-adr-constraints` Layer 1 this plan
falls under:

- changing evaluation, the runtime model or the dispatcher;
- designing a type;
- changing the public API;
- writing a decision record.

**Layer 3 rows checked.**

- ADR-001 × ADR-014: both checks stay.
- ADR-014 × ADR-017: `satisfyingSet` keeps its replicated check.
- ADR-003 × ADR-017: range extraction stays exhaustive.
- ADR-025 × ADR-017: an unknown element still fails evaluation and never
  enters a set.
- ADR-025 × ADR-003: the sample is drawn from the range in domain order.

Three new rows are owed. Phase 3 writes each into both records' cross-ADR
sections and into Layer 3:

- **ADR-026 × ADR-025.** A set answer reads as two-valued, and ADR-025 makes
  evaluation three-valued. The answer is two-valued because the law forbids a
  refusal at any element. The engine's own sets are three-way splits, so
  `p ∧ q` with `p` unknown and `q` false is false, as ADR-025 §1 states.
- **ADR-026 × ADR-017.** `~p` reads as needing every element of the domain.
  It is the candidates minus `p`'s true and unknown elements. At the root the
  candidates are the domain, so ADR-017 §1's closed-world complement holds.
- **ADR-026 × ADR-024.** `partition` is a new recursion over the formula
  tree, and ADR-024 §5 asks for a measurement against both limits.

**Layer 4.** No register record becomes wrong.
- The row "ADR-001, ADR-015 | ADR-028 §1–§2, §4" describes register's
  integration through `MapDispatcher` and `evaluateTyped`. Both keep their
  meaning, and `MapDispatcher` gains an optional field.
- Register's override of the new method is register's work, in its own plan.
  §10 writes it into register's task list once.

---

## 4. Grounding

### 4.1 The law a dispatcher keeps

For a predicate `name`, fixed arguments `before` and `after`, and the sort `S`
of the position between them, a dispatcher whose `satisfyingCarriers(name,
before, after)` is `Some(cs)` keeps these:

1. For every element `e` of `S`'s domain, `evalPredicate(name, before ++ (e ::
   after))` is `Right(b)`, never `Left`.
2. That `b` is `cs.contains(e.raw)`.
3. `evalPredicate`, `evalFunction` and `satisfyingCarriers` give the same
   answer for the same arguments each time they are called.

Point 1 follows from ADR-025. A refusal makes an atom unknown at that element,
and a carrier set has no place for unknown. A dispatcher that would refuse at
any element declines. Point 3 lets the engine call once where per-element
evaluation calls once per element. A carrier outside `S`'s domain is ignored,
because the engine keeps only domain elements (§5.3, `answered`).

### 4.2 What today's code does

`core/src/main/scala/vql/typed/TypedSemantics.scala`, `satisfyingSet`:

```scala
case Some(domain) =>
  domain.foldLeft[Either[QueryError, Set[Value]]](Right(Set.empty)) { (acc, candidate) =>
    for
      sat <- acc
      ok  <- holds(formula, Map(variable.name -> candidate), model)
    yield if ok then sat + candidate else sat
  }
```

`collectRangeElements` folds the domain the same way with the range formula.
`collectSatisfyingSample` folds the sample, a `Set[Value]`, with the scope.

`core/src/main/scala/vql/typed/RuntimeModel.scala`:

```scala
trait RuntimeDispatcher:
  def evalFunction(name: SymbolName, args: List[Value]): Either[String, Any]
  def evalPredicate(name: SymbolName, args: List[Value]): Either[String, Boolean]
  def functionSymbols: Set[SymbolName]
  def predicateSymbols: Set[SymbolName]
```

### 4.3 The three-way split, and why it equals per-element evaluation

For a subformula φ and candidates C, `partition` returns `T`, the elements of
C where φ is true, and `U`, the elements where φ is unknown, each with the
refusal per-element evaluation meets there. Every other element of C is false
(`F`). Write `Tp`, `Up`, `Fp` for `p`'s split over the same C.

| φ | evaluated over | `T` | `U`, with the refusal kept |
|---|---|---|---|
| `True` | — | C | ∅ |
| `False` | — | ∅ | ∅ |
| an answered atom | — | the answer ∩ C | ∅ |
| a declined atom, `∀`, `∃` | each element of C | per element | per element |
| `¬p` | `p` over C | `Fp` | `Up` |
| `p ∧ q` | `p` over C, `q` over `Tp ∪ Up` | `Tp ∩ Tq` | `Uq ∩ Tp` keeps `q`'s; `Up ∩ (Tq ∪ Uq)` keeps `p`'s |
| `p ∨ q` | `p` over C, `q` over C \ `Tp` | `Tp ∪ Tq` | `Uq \ Up` keeps `q`'s; `Up \ Tq` keeps `p`'s |
| `p → q` | `p` over C, `q` over `Tp ∪ Up` | `Fp ∪ Tq` | `Uq ∩ Tp` keeps `q`'s; `Up \ Tq` keeps `p`'s |
| `p ↔ q` | both over C | (`Tp ∩ Tq`) ∪ (`Fp ∩ Fq`) | `Up` keeps `p`'s; `Uq \ Up` keeps `q`'s |

Each row is ADR-025's table read at one element. For `p ∧ q`:
- `evalFormula` evaluates `q` at an element exactly when `p` is not false
  there. That is the set `Tp ∪ Up`.
- `conjoin` gives false when either side is false.
- It gives `p`'s unknown when `p` is unknown and `q` is not false.
- It gives `q`'s unknown when `p` is true and `q` unknown.

Equality then follows by induction on the formula:
- Each row evaluates each operand at the same elements `evalFormula` does.
- An answered atom gives per-element evaluation's value at every candidate,
  by §4.1.
- A declined atom and a quantifier are evaluated per element.

**Example.** The domain is 10 000 nodes. The formula is
`leaf(x) /\ descendant_of(x, node_id("IT"))`. 6 000 nodes are leaves, 300
nodes sit under `IT`, and 40 of those are leaves. Both predicates are
answered.

| Step | Today | With this plan |
|---|---|---|
| `leaf(x)` | 10 000 `evalPredicate` | 1 `satisfyingCarriers`, 6 000 carriers kept |
| `node_id("IT")` | 10 000 `evalFunction` | 1 `evalFunction` |
| `descendant_of(x, …)` | 10 000 `evalPredicate` | 1 `satisfyingCarriers` over the 6 000 candidates, 300 carriers probed |
| `∧` | — | the 300 probed against the 6 000, giving 40 |

---

## 5. Target behaviour

### 5.1 The dispatcher — `core/src/main/scala/vql/typed/RuntimeModel.scala`, `MapDispatcher.scala`

`satisfyingCarriers` joins `RuntimeDispatcher`, the trait whose member
`def evalPredicate(name: SymbolName, args: List[Value]): Either[String, Boolean]`
it complements. No existing method answers a set. `Set[Any]` holds carriers,
erased as `evalFunction`'s result is (ADR-015).

```scala
/**
 * Computes the functions and predicates of a catalog over a model's values. A dispatcher may also
 * answer a predicate as a whole set (`satisfyingCarriers`), which lets evaluation skip calling
 * `evalPredicate` at each element (ADR-026).
 */
trait RuntimeDispatcher:
  def evalFunction(name: SymbolName, args: List[Value]): Either[String, Any]
  def evalPredicate(name: SymbolName, args: List[Value]): Either[String, Boolean]
  def functionSymbols: Set[SymbolName]
  def predicateSymbols: Set[SymbolName]

  /**
   * The carriers of every element `e` for which `name` holds with arguments `before ++ (e :: after)`,
   * or `None` to decline. An answer must keep the law: at every element of the open position's
   * domain, `evalPredicate` refuses nothing and agrees with the answer, and every method answers the
   * same arguments the same way each time (ADR-026). `SatisfyingCarriersLaw.check` tests it.
   *
   * {{{
   * // leaf is true for "a1" and "a3"
   * satisfyingCarriers(SymbolName("leaf"), Nil, Nil) == Some(Set("a1", "a3"))
   * }}}
   */
  def satisfyingCarriers(name: SymbolName, before: List[Value], after: List[Value]): Option[Set[Any]] =
    None
```

`MapDispatcher` gains a field after `functions`. The family is `MapDispatcher`'s
own maps, of which `functions: Map[SymbolName, List[Value] => Either[String,
Any]] = Map.empty` is one.

```scala
case class MapDispatcher(
  predicates: Map[SymbolName, List[Value] => Either[String, Boolean]],
  functions: Map[SymbolName, List[Value] => Either[String, Any]] = Map.empty,
  satisfyingSets: Map[SymbolName, (List[Value], List[Value]) => Option[Set[Any]]] = Map.empty)
  extends RuntimeDispatcher:
  require(
    satisfyingSets.keySet.subsetOf(predicates.keySet),
    "MapDispatcher: a satisfying set is given for a predicate with no implementation",
  )

  override def satisfyingCarriers(name: SymbolName, before: List[Value], after: List[Value]): Option[Set[Any]] =
    satisfyingSets.get(name).flatMap(_(before, after))
```

### 5.2 Element sets — `core/src/main/scala/vql/typed/ElementSet.scala`, new

`ElementSet` is a new type. No existing type holds a subset of a domain that
may be complemented. `OrderedDomain` (`final case class OrderedDomain private
(carriers: Vector[Any])`) holds a whole domain. Every explicit set is a subset
of the domain it is taken in, and every operation keeps that true.

```scala
package vql.typed

/**
 * A subset of one domain: the listed elements, or every element of the domain but the excluded
 * ones. Each operation costs the sizes of the explicit sets it reads, never the domain's size.
 */
private[typed] enum ElementSet:
  case Listed(elements: Set[Value])
  case AllBut(excluded: Set[Value])

private[typed] object ElementSet:

  val all: ElementSet  = AllBut(Set.empty)
  val none: ElementSet = Listed(Set.empty)

  def contains(s: ElementSet, e: Value): Boolean =
    s match
      case Listed(es) => es.contains(e)
      case AllBut(xs) => !xs.contains(e)

  def complement(s: ElementSet): ElementSet =
    s match
      case Listed(es) => AllBut(es)
      case AllBut(xs) => Listed(xs)

  def intersect(a: ElementSet, b: ElementSet): ElementSet =
    (a, b) match
      case (Listed(x), Listed(y)) => Listed(if x.size <= y.size then x.filter(y.contains) else y.filter(x.contains))
      case (Listed(x), AllBut(y)) => Listed(x -- y)
      case (AllBut(x), Listed(y)) => Listed(y -- x)
      case (AllBut(x), AllBut(y)) => AllBut(x ++ y)

  def union(a: ElementSet, b: ElementSet): ElementSet =
    complement(intersect(complement(a), complement(b)))

  /** True when `s` holds no element of `view`'s domain. */
  def isEmpty(s: ElementSet, view: DomainView): Boolean =
    s match
      case Listed(es) => es.isEmpty
      case AllBut(xs) => xs.size == view.ordered.size

  /** The elements of `s` in `view`'s domain order. */
  def ordered(s: ElementSet, view: DomainView): Vector[Value] =
    s match
      case Listed(es) => es.toVector.sortBy(view.position)
      case AllBut(xs) => view.ordered.filterNot(xs.contains)

end ElementSet
```

`DomainView` joins `RuntimeModel.scala`, beside `OrderedDomain`, which it
indexes. `RuntimeModel` gains one lazy field. Its existing `elements` field
stays.

```scala
/** One domain's elements in declared order, the same elements as a set, and each one's position. */
private[typed] final case class DomainView(ordered: Vector[Value], members: Set[Value], position: Map[Value, Int])

// inside case class RuntimeModel
/** Each domain's elements indexed for set evaluation, built on first use. */
private[typed] lazy val views: Map[TypeId, DomainView] =
  elements.map((sort, values) => sort -> DomainView(values, values.toSet, values.zipWithIndex.toMap))
```

### 5.3 The evaluator — `core/src/main/scala/vql/typed/TypedSemantics.scala`

`Partition` is a new private type. No existing type holds a three-way split.
`Truth` (`case Known(value: Boolean)`, `case Unknown(refusal:
QueryError.EvaluationError)`) holds one element's value.

```scala
/**
 * A subformula's value at each candidate: true at `holding`, unknown at each key of `unknown` with
 * the refusal that left it so, and false at every other candidate.
 */
private final case class Partition(holding: ElementSet, unknown: Map[Value, QueryError.EvaluationError])
```

The public entries keep their signatures:

```scala
def evaluate(
  query: BoundQuery,
  model: RuntimeModel,
  answerTuple: Map[String, Value] = Map.empty,
  samplingParams: SamplingParams = SamplingParams.exact,
  hdrConfig: HDRConfig = HDRConfig.default,
): Either[QueryError, EvaluationOutput[Value]]

def satisfyingSet(formula: BoundFormula, variable: BoundVar, model: RuntimeModel): Either[QueryError, Set[Value]] =
  model.views.get(variable.sort) match
    case None       => Left(QueryError.DomainNotFoundError(typeName = variable.sort, availableTypes = model.domains.keySet))
    case Some(view) =>
      partition(formula, variable, ElementSet.all, Map.empty, model, view)
        .flatMap(settle(_, view))
        .map(ElementSet.ordered(_, view).toSet)
```

The private functions that change. `evaluate` looks up `model.views` where it
looks up `model.elements` today, and passes the view on.

```scala
private def collectRangeElements(
  query: BoundQuery,
  model: RuntimeModel,
  baseEnv: Env,
  view: DomainView,
): Either[QueryError, Vector[Value]] =
  partition(query.range, query.variable, ElementSet.all, baseEnv, model, view)
    .flatMap(settle(_, view))
    .map(ElementSet.ordered(_, view))

private def evaluateOverRange(
  query: BoundQuery,
  model: RuntimeModel,
  baseEnv: Env,
  view: DomainView,
  rangeElements: Vector[Value],
  quantifier: VagueQuantifier,
  samplingParams: SamplingParams,
  hdrConfig: HDRConfig,
): Either[QueryError, EvaluationOutput[Value]]

private def collectSatisfyingSample(
  query: BoundQuery,
  model: RuntimeModel,
  baseEnv: Env,
  view: DomainView,
  sample: Set[Value],
): Either[QueryError, Set[Value]] =
  partition(query.scope, query.variable, ElementSet.Listed(sample), baseEnv, model, view)
    .flatMap(settle(_, view))
    .map(ElementSet.ordered(_, view).toSet)
```

The new private functions:

```scala
/** The true elements, or the refusal at the unknown element first in domain order. */
private def settle(p: Partition, view: DomainView): Either[QueryError, ElementSet] =
  p.unknown.minByOption((e, _) => view.position(e)) match
    case Some((_, refusal)) => Left(refusal)
    case None               => Right(p.holding)

/** `formula`'s value at every candidate, split three ways (ADR-026). */
private def partition(
  formula: BoundFormula,
  variable: BoundVar,
  candidates: ElementSet,
  env: Env,
  model: RuntimeModel,
  view: DomainView,
): Either[QueryError, Partition] =
  def over(f: BoundFormula, c: ElementSet): Either[QueryError, Partition] =
    partition(f, variable, c, env, model, view)
  formula match
    case BoundFormula.True                         => Right(Partition(candidates, Map.empty))
    case BoundFormula.False                        => Right(Partition(ElementSet.none, Map.empty))
    case atomic @ BoundFormula.Atom(atom)          =>
      openPosition(atom, variable) match
        case Some((before, after)) => answered(atom, before, after, variable, candidates, env, model, view)
        case None                  => perElement(atomic, variable, candidates, env, model, view)
    case BoundFormula.Not(p)                       =>
      over(p, candidates).map(pp => Partition(falseAt(candidates, pp), pp.unknown))
    case BoundFormula.And(p, q)                    =>
      for
        pp <- over(p, candidates)
        pq <- over(q, notFalseAt(pp))
      yield Partition(
        ElementSet.intersect(pp.holding, pq.holding),
        pq.unknown.filter((e, _) => ElementSet.contains(pp.holding, e)) ++
          pp.unknown.filter((e, _) => ElementSet.contains(pq.holding, e) || pq.unknown.contains(e)),
      )
    case BoundFormula.Or(p, q)                     =>
      for
        pp <- over(p, candidates)
        pq <- over(q, ElementSet.intersect(candidates, ElementSet.complement(pp.holding)))
      yield Partition(
        ElementSet.union(pp.holding, pq.holding),
        pq.unknown.filter((e, _) => !pp.unknown.contains(e)) ++
          pp.unknown.filter((e, _) => !ElementSet.contains(pq.holding, e)),
      )
    case BoundFormula.Imp(p, q)                    =>
      for
        pp <- over(p, candidates)
        pq <- over(q, notFalseAt(pp))
      yield Partition(
        ElementSet.union(falseAt(candidates, pp), pq.holding),
        pq.unknown.filter((e, _) => ElementSet.contains(pp.holding, e)) ++
          pp.unknown.filter((e, _) => !ElementSet.contains(pq.holding, e)),
      )
    case BoundFormula.Iff(p, q)                    =>
      for
        pp <- over(p, candidates)
        pq <- over(q, candidates)
      yield Partition(
        ElementSet.union(
          ElementSet.intersect(pp.holding, pq.holding),
          ElementSet.intersect(falseAt(candidates, pp), falseAt(candidates, pq)),
        ),
        pp.unknown ++ pq.unknown.filter((e, _) => !pp.unknown.contains(e)),
      )
    case quantified @ (BoundFormula.Forall(_, _) | BoundFormula.Exists(_, _)) =>
      perElement(quantified, variable, candidates, env, model, view)

/** The candidates where the split's subformula is false. */
private def falseAt(candidates: ElementSet, p: Partition): ElementSet =
  ElementSet.intersect(
    ElementSet.intersect(candidates, ElementSet.complement(p.holding)),
    ElementSet.AllBut(p.unknown.keySet),
  )

/** The candidates where the split's subformula is true or unknown. */
private def notFalseAt(p: Partition): ElementSet =
  ElementSet.union(p.holding, ElementSet.Listed(p.unknown.keySet))

/**
 * The arguments before and after the atom's one argument that is exactly `variable`, when no other
 * argument mentions it.
 *
 * {{{
 * descendant_of(x, node_id("IT")) gives Some((Nil, List(node_id("IT"))))
 * descendant_of(x, x)             gives None
 * }}}
 */
private def openPosition(atom: BoundAtom, variable: BoundVar): Option[(List[BoundTerm], List[BoundTerm])] =
  val (before, rest) = atom.args.span(!isVariable(_, variable))
  rest match
    case _ :: after if !(before ++ after).exists(mentions(_, variable)) => Some((before, after))
    case _                                                               => None

private def isVariable(term: BoundTerm, variable: BoundVar): Boolean =
  term match
    case BoundTerm.VarRef(v)                                                => v.name == variable.name
    case BoundTerm.ConstRef(_, _) | BoundTerm.LiteralRef(_, _, _) | BoundTerm.FnApp(_, _, _) => false

private def mentions(term: BoundTerm, variable: BoundVar): Boolean =
  term match
    case BoundTerm.VarRef(v)                                      => v.name == variable.name
    case BoundTerm.FnApp(_, args, _)                              => args.exists(mentions(_, variable))
    case BoundTerm.ConstRef(_, _) | BoundTerm.LiteralRef(_, _, _) => false

/**
 * An atom whose arguments other than the variable are fixed. Asks the dispatcher once for its
 * satisfying set; evaluates per element when it declines.
 */
private def answered(
  atom: BoundAtom,
  before: List[BoundTerm],
  after: List[BoundTerm],
  variable: BoundVar,
  candidates: ElementSet,
  env: Env,
  model: RuntimeModel,
  view: DomainView,
): Either[QueryError, Partition] =
  if ElementSet.isEmpty(candidates, view) then Right(Partition(ElementSet.none, Map.empty))
  else
    val fixed =
      for
        b <- evalTerms(before, env, model)
        a <- evalTerms(after, env, model)
      yield (b, a)
    fixed match
      case Left(refusal @ QueryError.EvaluationError(EvaluationFailure.FunctionRefused(_, _))) =>
        Right(Partition(ElementSet.none, ElementSet.ordered(candidates, view).map(_ -> refusal).toMap))
      case Left(failure)                                                                        => Left(failure)
      case Right((b, a))                                                                        =>
        model.dispatcher.satisfyingCarriers(atom.name, b, a) match
          case Some(carriers) =>
            val holding = carriers.iterator
              .map(Value(variable.sort, _))
              .filter(e => view.members.contains(e) && ElementSet.contains(candidates, e))
              .toSet
            Right(Partition(ElementSet.Listed(holding), Map.empty))
          case None           => perElement(BoundFormula.Atom(atom), variable, candidates, env, model, view)

/** `formula` evaluated at each candidate in domain order, as per-element evaluation does. */
private def perElement(
  formula: BoundFormula,
  variable: BoundVar,
  candidates: ElementSet,
  env: Env,
  model: RuntimeModel,
  view: DomainView,
): Either[QueryError, Partition]
// folds ElementSet.ordered(candidates, view) through evalFormula(formula, env + (variable.name -> e), model):
// Known(true) adds e to holding, Known(false) adds nothing, Unknown(r) adds e -> r, Left passes up at once.
```

`holds` loses its three callers and is removed. `evalFormula`, `quantify`,
`classify`, `negate`, `conjoin`, `disjoin`, `evalAtom`, `evalTerms` and
`evalTerm` are unchanged.

### 5.4 The law check — `core/src/main/scala/vql/typed/SatisfyingCarriersLaw.scala`, new

`LawViolation` is a new type. It is a check result like `FragmentViolation` in
`vql/fragment/Fragment.scala` (`case QuantifierNotAllowed` is one of its
cases), and it holds `Value`s, so it sits in `vql.typed`. `dispatcherMessage`
is the dispatcher's own text, as in `EvaluationFailure.PredicateRefused`.

```scala
package vql.typed

/** What `SatisfyingCarriersLaw.check` found wrong with a dispatcher's set answer. */
enum LawViolation:
  case NoDomain(sort: TypeId)
  case Refused(element: Value, dispatcherMessage: String)
  case Disagrees(element: Value, evaluated: Boolean)

object SatisfyingCarriersLaw:

  /**
   * `Right(())` when the dispatcher declines `name` with these arguments, or when its answer agrees
   * with `evalPredicate` at every element of `sort`'s domain and `evalPredicate` refuses none;
   * otherwise the violation at the first element in domain order.
   */
  def check(
    model: RuntimeModel,
    name: SymbolName,
    sort: TypeId,
    before: List[Value],
    after: List[Value],
  ): Either[LawViolation, Unit]

end SatisfyingCarriersLaw
```

---

## 6. Implementation phases

Each phase compiles and passes the full suite on both platforms before the next
starts. The plan ships whole, as one release.

**Phase 1 — the dispatcher's question, element sets and the law check.**
1. `vql/typed/RuntimeModel.scala`: the trait's scaladoc and
   `satisfyingCarriers` (§5.1), `DomainView` and `views` (§5.2).
2. `vql/typed/MapDispatcher.scala` (§5.1). The scaladoc's usage block gains a
   `satisfyingSets` entry.
3. `vql/typed/ElementSet.scala` (§5.2).
4. `vql/typed/SatisfyingCarriersLaw.scala` (§5.4).
5. `core/src/test/scala/vql/typed/ElementSetSpec.scala`, new. Over a domain
   of six elements, for every pair drawn from 8 sets (4 listed, 4 complemented,
   including both empties), the elements of `intersect`, `union` and
   `complement` equal the same operation on the materialized sets. `isEmpty`
   and `ordered` agree with the materialized set in domain order.
6. `core/src/test/scala/vql/typed/SatisfyingCarriersLawSpec.scala`, new:
   - a dispatcher that declines gives `Right(())`;
   - one whose answer matches `evalPredicate` gives `Right(())`;
   - one whose answer adds an element gives `Disagrees(e, false)` at that
     element, and one that drops an element gives `Disagrees(e, true)`;
   - one whose `evalPredicate` refuses an element gives `Refused`;
   - a sort with no domain gives `NoDomain`.
7. `MapDispatcherSpec`: `satisfyingSets` answers through
   `satisfyingCarriers`, a predicate with no entry declines, and a key absent
   from `predicates` throws `IllegalArgumentException`.

**Phase 2 — the set evaluation.**
1. `vql/typed/TypedSemantics.scala` (§5.3). `holds` is removed.
2. `core/src/test/scala/vql/typed/SetEvaluationSpec.scala`, new.

   **The model.** The domain is six elements, `a1` to `a6`. The dispatcher
   holds:
   - `p` and `q`, answered as sets;
   - `r`, declined;
   - `s`, declined, which refuses at `a2` and `a5`;
   - `t(x, f("k"))`, answered, where `f` refuses for `"bad"` and returns its
     argument otherwise.

   A counting wrapper records every call.

   **The reference.** For a formula φ, the reference is `satisfyingSet` of
   `∀z . φ`, where `z` ranges over a one-element sort and does not occur in
   φ. A quantifier is evaluated per element, so the reference is today's
   per-element path. A quantifier over one binding has its body's value,
   under ADR-025 §1.

   **The tests:**
   - **Equivalence.** Every formula of these levels gives the reference's
     `Either`, the same set or the same refusal:
     - level 0: the five atoms, their negations, `True` and `False`;
     - level 1: every `∧`, `∨`, `→` and `↔` of two level-0 formulas;
     - level 2: every connective over two level-1 formulas built from `p`,
       `s`, `¬p` and `¬s`.
   - **Quantifiers.** `p(x) ∧ ∃y . s(y)` and `∀y . q(y) ∨ r(x)` give the
     reference's `Either`.
   - **Cost.** `p(x) ∧ q(x)` makes 0 `evalPredicate` calls and 2
     `satisfyingCarriers` calls. `r(x)` alone makes 6 `evalPredicate` calls,
     the same as the reference. `t(x, f("k"))` makes 1 `evalFunction` call.
   - **Restriction.** In `p(x) ∧ r(x)`, `r` is called only at `p`'s true
     elements. In `p(x) ∨ r(x)`, `r` is called only at the rest.
   - **A refusing fixed argument.** `t(x, f("bad"))` gives
     `Left(EvaluationError(FunctionRefused(f, …)))`, and `¬p(x) ∨ t(x,
     f("bad"))` gives the same refusal, as the reference does.
   - **Domain order.** `s(x)` gives the refusal at `a2`, not at `a5`, with the
     domain built in either input order.
   - **The sample.** Run on a domain of 200 identity-hashed carriers, with
     `SamplingParams.exact` and a scope refusing at two elements, the
     reported refusal is the one at the element first in domain order. That
     holds in both input orders (§2, settled point 8).

**Phase 3 — the pipeline, records and documents.**
1. `VagueSemanticsTypedSpec`, end to end through `VagueQueryParser.parse`,
   `FolModel`, `VagueSemantics.evaluateTyped` and
   `VagueSemantics.satisfyingSet`. The catalog declares `DomainType(asset)` and
   the predicates `p: (asset)`, `under: (asset, asset)` and `r: (asset)`. The
   dispatcher is a `MapDispatcher` answering `p` and `under` with
   `satisfyingSets`. Each of these equals its reference, the same query
   with a `MapDispatcher` that has no `satisfyingSets`:
   - `Q[>=]^{1/2} x (p(x) /\ under(x, "a1"), r(x))`;
   - `satisfyingSet` of `~p(x) \/ under(x, "a1")`;
   - the first query with `SamplingParams()` over 200 elements.
2. `core/.jvm/src/test/scala/parser/NestingBoundStackSpec.scala` gains one
   test. On a 1 MiB thread, `satisfyingSet` runs over a dispatcher answering
   `p` for two formulas: `"~ " * 256 + "p(x)"`, and 256 copies of `p(x)`
   joined by `\/`. Both are within ADR-024's limits. The same two formulas
   run in `VagueSemanticsTypedSpec`, which covers Node.
3. `docs/ADR-026.md`, new, "Set Evaluation of Satisfying Sets", following
   `docs/ADR-00X.md`:
   - **Context:**
     - per-element evaluation costs the domain size times the atom count;
     - a knowledge base often holds a predicate's answer as an index;
     - a set has no place for an unknown value;
     - a three-valued result over a subset of the domain needs a three-way
       split.
   - **Decision:**
     1. `satisfyingCarriers` and its law (§4.1);
     2. the three-way split and its table (§4.3);
     3. sets held listed or complemented (§5.2);
     4. fixed arguments evaluated once (§2, settled point 7);
     5. refusals settled by domain order;
     6. the three sites use it;
     7. a new connective or quantifier states its row of the table.
   - **Code smells:**
     - answering a predicate the dispatcher could refuse at some element;
     - a two-valued set for a subformula;
     - materializing a complement over the domain.
   - **Alternatives rejected:**
     - a second dispatcher trait;
     - an index for the open position;
     - all-or-nothing evaluation;
     - consumer-written law checks.
   - **Cross-ADR:** the three Layer 3 rows of §3, and ADR-015 for carriers.
   - **Implementation table:**
     - `vql/typed/RuntimeModel.scala`;
     - `vql/typed/MapDispatcher.scala`;
     - `vql/typed/ElementSet.scala`;
     - `vql/typed/TypedSemantics.scala`;
     - `vql/typed/SatisfyingCarriersLaw.scala`.
4. `docs/ADR-017.md`:
   - §6's bullet "**Exact and deterministic.** Enumerates
     `model.domains(sort(v))` and filters with the existing `evalFormula`"
     becomes "**Exact and deterministic.** The result is the domain filtered
     by `evalFormula`. The engine computes it by set evaluation where the
     dispatcher answers an atom (ADR-026)."
   - The implementation table's `TypedSemantics.scala` row becomes
     "`collectRangeElements` and `satisfyingSet` via `partition` over the
     whole domain (ADR-026)".
   - The cross-ADR section gains ADR-026.
5. `docs/ADR-025.md`:
   - §5's first sentence becomes: "Range extraction, scope evaluation,
     `satisfyingSet` and every quantifier visit a domain's elements in its
     declared order, and a set result is settled by declared order
     (ADR-026)."
   - The cross-ADR section gains ADR-026.
   - The implementation table's `TypedSemantics.scala` row gains `partition`
     and `settle`.
6. `docs/ADR-015.md`: the implementation table's `RuntimeModel.scala` row
   gains "`satisfyingCarriers` returns carriers, wrapped by the engine".
7. `.claude/skills/vql-adr-constraints/SKILL.md`:
   - Layer 2 gains the ADR-026 row.
   - Layer 1, "Changing evaluation, the runtime model or the dispatcher",
     gains ADR-026 in its always column.
   - Layer 1, "Changing the public API", gains "ADR-026 for
     `satisfyingCarriers`" in its also column.
   - Layer 3 gains ADR-026 × ADR-025, ADR-026 × ADR-017 and ADR-026 ×
     ADR-024.
   - Layer 4 gains no row (§3).
8. `docs/Architecture.md`:
   - The "Symbol resolution" paragraph gains a sentence on
     `satisfyingCarriers`.
   - The "Range extraction" paragraph becomes: range extraction evaluates the
     range over the domain by set evaluation and keeps the true elements in
     domain order.
   - The record table gains ADR-026.
9. `docs/VagueQuantifiers.md`: the rows "Extract D_R" and "Evaluate scope"
   name `collectRangeElements(query, model, env, view)` and
   `collectSatisfyingSample(query, model, env, view, sample)`.
10. `CHANGELOG.md`, 0.22.0:
    - "Changed (breaking)": `MapDispatcher` gains `satisfyingSets`.
    - "Added": `RuntimeDispatcher.satisfyingCarriers`,
      `SatisfyingCarriersLaw` and `LawViolation`.
    - "Fixed": a sampled scope reports the refusal at the element first in
      domain order.
11. `docs/TODOS.md`: T-016's status becomes "RESOLVED by
    `PLAN-predicate-satisfying-set` (ADR-026)".
12. The register task-list entry of §10, in the repository `register`.
13. `build.sbt`: the user runs
    `sed -i 's/^ThisBuild \/ version      := .*/ThisBuild \/ version      := "0.22.0"/' build.sbt`.
    The user runs the release per `docs/RELEASE.md`.

---

## 7. Verification plan

The tests added or changed are listed in §6. These commands must be green:

```bash
sbt "folEngineJVM/testOnly vql.typed.ElementSetSpec vql.typed.SatisfyingCarriersLawSpec vql.typed.MapDispatcherSpec vql.typed.SetEvaluationSpec vql.semantics.VagueSemanticsTypedSpec parser.NestingBoundStackSpec"
# expected: ends in [success]
sbt test
# expected: ends in [success]; both platforms, so Phase 3 item 2's formulas run on Node
```

Phase 3 item 1 is the end-to-end test `docs/WORKING-INSTRUCTIONS.md` requires
for a public surface change. It reaches `satisfyingCarriers` through
`VagueQueryParser.parse`, `FolModel`, `VagueSemantics.evaluateTyped` and
`VagueSemantics.satisfyingSet`.

After Phase 3, the mechanical check of `vql-adr-constraints` must print
nothing:

```sh
for f in docs/ADR-*.md; do grep -q "\`$(basename "$f")\`" .claude/skills/vql-adr-constraints/SKILL.md || echo "MISSING $f"; done
# expected: no output
```

---

## File inventory

- `core/src/main/scala/vql/typed/RuntimeModel.scala`
- `core/src/main/scala/vql/typed/MapDispatcher.scala`
- `core/src/main/scala/vql/typed/ElementSet.scala` (new)
- `core/src/main/scala/vql/typed/SatisfyingCarriersLaw.scala` (new)
- `core/src/main/scala/vql/typed/TypedSemantics.scala`
- `core/src/test/scala/vql/typed/ElementSetSpec.scala` (new)
- `core/src/test/scala/vql/typed/SatisfyingCarriersLawSpec.scala` (new)
- `core/src/test/scala/vql/typed/SetEvaluationSpec.scala` (new)
- `core/src/test/scala/vql/typed/MapDispatcherSpec.scala`
- `core/src/test/scala/vql/semantics/VagueSemanticsTypedSpec.scala`
- `core/.jvm/src/test/scala/parser/NestingBoundStackSpec.scala`
- `docs/ADR-026.md` (new)
- `docs/ADR-017.md`
- `docs/ADR-025.md`
- `docs/ADR-015.md`
- `.claude/skills/vql-adr-constraints/SKILL.md`
- `docs/Architecture.md`
- `docs/VagueQuantifiers.md`
- `CHANGELOG.md`
- `docs/TODOS.md`
- `build.sbt` (the version line, run by the user)

Outside this repository, one file in `register`, written once in Phase 3:
`docs/dev/TODO.md`.

---

## 9. Consumer-document impact

- `docs/Architecture.md`, "Symbol resolution" and "Range extraction". Stale;
  Phase 3 item 8.
- `docs/VagueQuantifiers.md`, the "Extract D_R" and "Evaluate scope" rows,
  which name `holds`. Stale; Phase 3 item 9.
- `README.md` describes a dispatcher that "decides each predicate", which
  stays true. Not affected.
- Scaladoc: `RuntimeDispatcher` and its new method, `MapDispatcher` and its
  usage block, and `TypedSemantics.satisfyingSet`. Brought to state in
  Phases 1 and 2.

---

## 10. What register must do, named once

This is written into `register/docs/dev/TODO.md` as one item in Phase 3.

- `build.sbt` line 40: `vqlEngineVersion` to `0.22.0`.
- `RiskTreeKnowledgeBase`'s `MapDispatcher` passes `satisfyingSets` for the
  six predicates of `PLAN-TARGETING-SET-EVALUATION.md` §3. Each entry
  receives `before` and `after`, for example `Nil` and
  `List(Value(Node, P))` for `descendant_of(x, node_id("P"))`. It answers
  with the `NodeId`s, as carriers, and declines any other argument shape.
- `PLAN-TARGETING-SET-EVALUATION.md` §4 quotes §5.1's `satisfyingCarriers`
  and `MapDispatcher.satisfyingSets`. Its §5 law tests may call
  `SatisfyingCarriersLaw.check`.
