# Implementation Plan: Order-Independent Evaluation

**Status:** APPROVED (user, 2026-11-15). `OIE-D-1` is ruled B, and no
decision is open (§2).
**Version this was read at:** 0.20.0.
**Target version:** 0.21.0. Under early-semver a pre-1.0 breaking change bumps
the minor version. This plan changes the type of `RuntimeModel.domains` and the
population parameter of every public sampling entry. A consumer that builds a
`RuntimeModel` or calls a sampler stops compiling until it adapts. It also
changes results a consumer can observe: a query that failed on a dispatcher
error can now return a result.
**Plan code:** `OIE`, the initials of the filename slug
`order-independent-evaluation`. Decisions are `OIE-D-<n>`.
**Origin:** `docs/TODOS.md` T-009 and T-010.
- T-009: the user ruled on 2026-11-15 that a dispatcher error is an unknown
  truth value under Kleene's strong three-valued logic (the option labelled C
  in the session that ruled it).
- T-010: the user asked on 2026-11-15 for a plan based on the stable total
  order discussed in the same session: an order the consumer declares per
  domain sort, applied once, with the sampler selecting by position in it.

**Records this plan creates and amends:** it creates `docs/ADR-025.md`. It
amends `docs/ADR-017.md` §7, `docs/ADR-003.md` §2 and `docs/ADR-001.md` §4. Why
a new record is needed is §2, settled point 6.

---

## 0. Executive summary

**What goes wrong today.** Two results depend on the order in which a `Set`
iterates. A Scala `Set` keeps no order of its own. It iterates in an order
derived from its elements' `hashCode`. That order is fixed across runs only
when `hashCode` is computed from the element's content.

1. **A dispatcher error depends on order.** A dispatcher is the consumer's code
   that computes each predicate and function. When it cannot compute a value it
   returns `Left`, and that `Left` is a dispatcher error. `TypedSemantics`
   evaluates the left operand of `∧`, `∨` and `→` first, and stops when that
   operand decides the result. It passes on an error from the left operand at
   once.
   - `p(x) ∧ q(x)`, where `p` is false and `q` errors, gives `false`.
   - `q(x) ∧ p(x)` on the same element gives the error.
   - `∃a . r(x, a)`, where one binding errors and another makes `r` true,
     errors or succeeds depending on which binding the domain's `Set` yields
     first.
2. **A sample depends on order.** `HDRSampler.sample` copies the population
   into an array in `Set` order and swaps elements by position. The HDR
   generator fixes the positions. The elements at those positions depend on
   the `Set` order, so a carrier whose `hashCode` comes from object identity
   gives a different sample on each run.

**What changes.**

1. **A dispatcher error is an unknown truth value.** Evaluation combines truth
   values under Kleene's strong three-valued logic, defined in §4.1. A formula
   fails only when the error could change its result. The result no longer
   depends on the order of the operands or of the domain.
2. **Each domain holds its elements in an order the consumer declares.** The
   consumer builds each domain with `OrderedDomain.of`, which sorts the
   carriers once with an `Ordering` for the carrier type. Evaluation walks
   that order, and the sampler selects by position in it.

**What does not change.**
- On a model whose dispatcher never errors, every result is the same as today,
  and evaluation calls the dispatcher on the same atoms. Short-circuiting stays
  wherever the evaluated operand decides the result.
- `VagueSemantics.evaluateTyped`, `VagueSemantics.satisfyingSet`,
  `TypedSemantics.evaluate` and `TypedSemantics.satisfyingSet` keep their
  signatures.
- An error that is not a dispatcher refusal still fails evaluation at once.

---

## 1. Requirements

1. **A formula's result does not depend on operand order.** For every model
   and every element, `p ∧ q` and `q ∧ p` give the same result, and so do
   `p ∨ q` and `q ∨ p`. "Result" means the truth value, or the fact that
   evaluation failed.
2. **A quantifier's result does not depend on domain order.** Building the
   same domain from the same carriers in a different order gives the same
   result for every formula.
3. **A formula fails on a dispatcher error only when the error could change its
   result.** `false ∧ unknown` is `false`, and `true ∨ unknown` is `true`.
4. **A sample does not depend on how the domain's carriers were collected.** Two
   models built from equal carriers, in any input order and with any
   `hashCode`, select the same sample for the same `HDRConfig`.
5. **A model with no dispatcher errors gives every result it gives today.**
6. **Which refusal is reported is fixed.** When several dispatcher errors make
   a formula unknown, the one reported is the first in evaluation order, and
   evaluation order follows the domain's declared order.

### Not in scope

- An error that is not a dispatcher refusal stays a failure that passes on at
  once: `UnboundVariableError`, `DomainNotFoundError` and
  `EvaluationFailure.VariableSortMismatch`. Each is a defect of the engine or
  of a bypassed pipeline, not a value the model lacks. The ruling names
  dispatcher errors only (§2, settled point 1).
- `FOLSemantics` in the foundation keeps its `Set`-based `Domain[D]`. It is in
  ADR-007's scope tables and is not on the typed evaluation path (ADR-001).

---

## 2. Decisions for the user

### OIE-D-1: where the consumer declares a domain's order

**Ruled B** (user, 2026-11-15).

**Why it matters now.** The order has to come from the consumer, because
`Value.raw` is `Any` and only the consumer knows the carrier type (§4.2). Where
the consumer declares it fixes the shape of `RuntimeModel`. That is the type
every consumer builds, so the choice is part of 0.21.0's breaking change.

**Option A: an order beside each domain, applied when the `FolModel` is
built.** This is the form sketched in the session.

```scala
trait DomainOrder:
  def sorted(values: Set[Value]): Either[String, Vector[Value]]

object DomainOrder:
  def of[A](using ord: Ordering[A], ex: Extract[A]): DomainOrder

case class RuntimeModel(
  domains: Map[TypeId, Set[Value]],
  domainOrders: Map[TypeId, DomainOrder],
  dispatcher: RuntimeDispatcher)
```

`FolModel.apply` sorts each domain and stores the sorted vectors. Three
`RuntimeModelError` cases are added: a domain with no order, a value its
`Extract` cannot read, and two distinct values that compare equal.

- Pros:
  - Consumers keep building domains as `Set[Value]`.
  - Every failure is a typed refusal in the existing validation list.
- Cons:
  - The order and the domain sit in two maps that can disagree. That needs a
    validation case for a missing order.
  - Each value is read back through `Extract` only to be compared. That needs
    a validation case for a value the order cannot read.
  - The sorted vectors live in `FolModel`, so `TypedSemantics.evaluate` and
    `TypedSemantics.satisfyingSet` must take a `FolModel` in place of a
    `RuntimeModel`. Register calls `TypedSemantics.satisfyingSet(bound,
    boundVar, kb.model)` directly, in the repository `register`,
    `modules/server/.../MitigationScopeResolver.scala` line 117. That call
    changes too.
- What you would see: register's `RiskTreeKnowledgeBase` keeps its two
  `Set[Value]` domains. It adds a `domainOrders` map with two
  `DomainOrder.of[NodeId]`-style entries, and changes one
  `TypedSemantics.satisfyingSet` call.

**Option B: the domain is built sorted, `OrderedDomain.of`.**

```scala
final case class OrderedDomain private (carriers: Vector[Any])

object OrderedDomain:
  def of[A](carriers: Iterable[A])(using ordering: Ordering[A]): OrderedDomain

case class RuntimeModel(
  domains: Map[TypeId, OrderedDomain],
  dispatcher: RuntimeDispatcher)
```

The consumer passes its carriers typed as `A` together with an `Ordering[A]`.
`of` sorts them once. `RuntimeModel` builds each sort's `Value`s from them.

- Pros:
  - A domain cannot exist without its order, so no validation case is needed
    for a missing one.
  - The carriers are compared as `A`, before any erasure to `Any`, so nothing
    is read back and no read can fail.
  - `TypedSemantics` and `VagueSemantics` keep their signatures, register's
    direct `satisfyingSet` call included. Every `RuntimeModel` holds the order,
    including one used without a `FolModel`.
- Cons:
  - Every `RuntimeModel` construction changes. That is 8 files in this
    repository (§6) and 1 in register.
  - An `Ordering` that ranks two distinct carriers equal is refused by
    `require` at construction, an `IllegalArgumentException`, not by a typed
    refusal. ADR-012 puts it there: "`require` for what a value is". A domain
    whose order cannot separate its elements is a malformed value.
- What you would see: register's `RiskTreeKnowledgeBase` replaces
  `tree.index.nodes.keys.map(id => Value(nodeSort, id)).toSet` with
  `OrderedDomain.of(tree.index.nodes.keys)(using Ordering.by(_.value))`, and
  the same for mitigations. `NodeId.value` and `MitigationId.value` are the
  ULID strings, in register's `OpaqueTypes.scala` lines 933 and 954. Nothing
  else in register changes for this decision.

**My recommendation: B.** It makes the order part of the domain, which removes
two of A's three failure cases by construction. It also keeps the evaluation
signatures, so register's direct call stays as it is. The cost is a one-line
change at every place that builds a domain, which a consumer makes once.

### Settled by derivation

1. **Only a dispatcher refusal is unknown.** The ruling reads "a dispatcher
   error is an unknown truth value". `TypedSemantics` turns a dispatcher's
   `Left` into `EvaluationError(PredicateRefused(…))` or
   `EvaluationError(FunctionRefused(…))`. Those two become unknown. Every other
   `Left` fails at once (§1, "Not in scope").

2. **A function refusal makes its atom unknown.** A function is called only
   while evaluating the terms of an atom. When it refuses, the atom has no
   value, so the atom is unknown. Kleene's logic has no unknown term, only an
   unknown truth value.

3. **An unknown range or scope fails the element, and the query.** The range
   and the scope each decide one element's membership. An unknown truth value
   means membership is undecided, so evaluation fails with the refusal that
   made it unknown. On a model with no refusals this cannot occur. That keeps
   requirement 5.

4. **`↔` evaluates both sides, as today.** Neither side decides `p ↔ q`
   alone. If either side is unknown, the result is unknown.

5. **Evaluation walks the declared order everywhere.** Range extraction,
   scope evaluation, `satisfyingSet` and the inner quantifiers all read the
   domain's sorted vector. That makes requirement 6 hold. It needs no extra
   work beyond reading the vector in place of the `Set`.

6. **A new record, ADR-025, holds both rules.** Both constrain future work in
   a way no existing record states:
   - A new connective or quantifier must combine unknown truth values under
     §4.1's tables.
   - A new walk over a domain, or a new sampling entry, must read the declared
     order.

   ADR-017 §7 records the current short-circuit behaviour, and ADR-003 records
   the sampler. Each is amended to point at ADR-025.

7. **Every sampling entry takes an `IndexedSeq`.** An `IndexedSeq` is a
   sequence with fixed positions. `Sampler.sample`, `sampleWithPredicate`,
   `Sampler.autoSample`, `ProportionEstimator.estimateWithSampling`,
   `adaptiveEstimate` and `batchEstimate` all take a population as `Set[A]`
   today. Each one would reintroduce T-010 for its caller, so each takes
   `IndexedSeq[A]`. No main-code caller other than `TypedSemantics` uses them.
   Register uses none of them.

8. **Duplicates are removed by equality, then ties are refused.**
   `OrderedDomain.of` drops equal carriers with `distinct`, matching the `Set`
   that a domain is today. Two carriers that are distinct but compare equal
   would keep their input order after `sorted`, which reintroduces T-010.
   `of` therefore requires every adjacent pair to compare strictly less.

---

## 3. ADR compliance review (planning phase)

| ADR | Verdict | Basis |
|---|---|---|
| ADR-001 (many-sorted binding) | Relevant, **amended** | §4's code block shows `domains: Map[TypeId, Set[Value]]`, which becomes `Map[TypeId, OrderedDomain]`. `validateAgainst` keeps every check it makes. The typed pipeline stays the only evaluation path, and `bind` is not touched. Phase 3 amends §4's code block. |
| ADR-002 (parser-combinator style) | Not relevant | No parser is touched. |
| ADR-003 (HDR sampling) | Relevant, **amended** | §2's code block shows `sample(population: Set[A], n: Int)`. The HDR generator, Fisher-Yates selection and the single exact-or-sampled path all stay. The population becomes `IndexedSeq[A]`, and the selection becomes a function of the population's order. Phase 3 amends §2 and states the reproducibility condition. |
| ADR-004 (layering) | Relevant, compliant | `OrderedDomain` lives in `vql.typed` and imports nothing new. `vql.sampling` still imports nothing from `vql.typed`. |
| ADR-006 (enum vs sealed trait) | Relevant, compliant | `Truth` is a private pure-data `enum`. `OrderedDomain` is a case class with a private constructor. New code follows §4 and §5. |
| ADR-007 (OCaml-ported core) | Not relevant | No file in its scope tables is touched. `FOLSemantics.Domain` stays. |
| ADR-012 (error channel) | Relevant, compliant | The tie check is a `require` on what an `OrderedDomain` is. Evaluation keeps its `Either` channel, and an unknown truth value leaves it as the `EvaluationError` that caused it. |
| ADR-014 (quantifiability) | Relevant, compliant | The startup check still finds a missing domain by `domains.keySet`. The bind-time `TypeNotQuantifiable` check is not touched. |
| ADR-015 (value boundaries) | Relevant, compliant | Dispatchers keep reading `args(i).extract[A]`. `OrderedDomain.of` compares carriers as `A`, before they are wrapped, so no cast is added. `carriers: Vector[Any]` is erased like `Value.raw`, which ADR-015 accepts. |
| ADR-016 (carrier witness) | Not relevant, Proposed | `LiteralRef.value` is not touched. |
| ADR-017 (formula ranges) | Relevant, **amended** | §2 holds: range extraction stays exhaustive over the whole domain. §6 holds: `satisfyingSet` stays exhaustive and keeps its replicated `TypeNotQuantifiable` check. §7's two bullets on short-circuiting and on `Set` order become false. Phase 3 replaces them with a pointer to ADR-025. |
| ADR-018 (fragment membership) | Not relevant | `FragmentCheck` reads the parse tree, not a model. |
| ADR-020 (binder error accumulation) | Not relevant | The binder is not touched. |
| ADR-022 (typed refusals) | Relevant, compliant | No refusal case is added or reshaped. `PredicateRefused` and `FunctionRefused` reach the caller unchanged when they decide a result. |
| ADR-023 (identifiers in `vql.id`) | Relevant, compliant | No error type is added. `OrderedDomain` holds no sort and no symbol. |
| ADR-024 (parse depth bound) | Relevant, compliant | §5 asks that a new recursive walk of the tree be measured. `evalFormula` keeps its recursion depth. `quantify` replaces `allOf` and `anyOf` and is tail-recursive like them. |

**Deviations detected:** none.

**What the review read.** Every record in force was read on 2026-11-15, and
ADR-016 (Proposed). The activities of `vql-adr-constraints` Layer 1 this plan
falls under:

- changing evaluation, the runtime model or the dispatcher;
- changing sampling or estimation;
- designing a type;
- changing the public API;
- writing a decision record.

**Layer 3 rows checked.**

- ADR-001 × ADR-014: the startup half of the quantifiability check stays in
  `validateAgainst`.
- ADR-014 × ADR-017: `satisfyingSet` keeps its replicated check.
- ADR-003 × ADR-017: range extraction stays exhaustive. Only the scope is
  sampled.
- No other row involves a record this plan touches.

Two new rows are owed. Phase 3 writes both into the records' cross-ADR
sections and into Layer 3:

- **ADR-025 × ADR-017.** ADR-017 reads the range as set algebra: `∧` is an
  intersection and `¬` a complement. That reading holds for every result
  evaluation returns. An element whose membership is unknown fails evaluation
  and never enters a set.
- **ADR-025 × ADR-003.** ADR-003 claims a sample is reproducible. That holds
  only for a population in a fixed order. The domain's declared order supplies
  that order.

**Layer 4.** The row "ADR-001, ADR-015 | ADR-028 §1–§2, §4" applies. In the
repository `register`, two passages state the old domain type:

- `docs/dev/decision-records/ADR-028-appendix-technical-design.md` line 19
  says the domain is "`RuntimeModel.domains(sort): Set[Value]`".
- `docs/dev/decision-records/ADR-028-vague-quantifier-query-pane.md` line 54
  says "The class builds the `RuntimeModel`: one domain of `Value`s per".

Both become wrong under `OIE-D-1` option B. That is register's to amend, and
§10 writes it into register's task list once. Phase 3 extends the Layer 4 row
to name the appendix table.

---

## 4. Current behaviour and the target logic (grounding)

### 4.1 Kleene's strong three-valued logic

Kleene's strong logic adds a third truth value, *unknown*, to true and false.
An operator gives a known value whenever that value would be the same for
every way the unknown could resolve. Otherwise it gives unknown. Here `U`
marks the unknown value:

| `p` | `q` | `p ∧ q` | `p ∨ q` | `p → q` | `p ↔ q` |
|---|---|---|---|---|---|
| T | U | U | T | U | U |
| F | U | F | U | T | U |
| U | T | U | T | T | U |
| U | F | F | U | U | U |
| U | U | U | U | U | U |

`¬U` is `U`. Rows with no `U` are classical. `∀` is false if any binding is
false, otherwise unknown if any binding is unknown, otherwise true. `∃` is the
dual: true if any binding is true, otherwise unknown if any binding is unknown,
otherwise false.

Each value in the table depends only on the set of operand values, so it does
not depend on operand order or binding order. That property gives
requirements 1 and 2.

### 4.2 Why the engine cannot pick the order itself

`Value` is `case class Value(sort: TypeId, raw: Any)` in
`core/src/main/scala/vql/typed/RuntimeModel.scala`. The engine never knows the
type behind `raw`. Two generic keys suggest themselves, and both fail:

- `hashCode` is the source of the problem in the first place. It also has
  collisions.
- `toString` can print the same text for two distinct carriers, and then the
  order is not total.

The consumer knows the carrier type and can give an `Ordering` for it, as it
already gives an `Extract` instance under ADR-015.

### 4.3 What the evaluator does today

`evalFormula` in `core/src/main/scala/vql/typed/TypedSemantics.scala` returns
`Either[QueryError, Boolean]`:

```scala
case BoundFormula.And(p, q)       =>
  evalFormula(p, env, model).flatMap {
    case false => Right(false)
    case true  => evalFormula(q, env, model)
  }
```

A `Left` from `p` passes through `flatMap` without evaluating `q`. `allOf` and
`anyOf` walk `domain.toList`, where `domain` is the `Set[Value]`. Each returns
the first `Left` it meets.

---

## 5. Target behaviour

### 5.1 The domain — `core/src/main/scala/vql/typed/RuntimeModel.scala`

`OrderedDomain` is a new type. It joins the model types in
`RuntimeModel.scala`, beside `case class Value(sort: TypeId, raw: Any)`. The
only existing type for a domain's elements is `semantics.Domain[D](elements:
Set[D])` in `core/src/main/scala/semantics/FOLSemantics.scala`. That type is
ported foundation code under ADR-007, holds no order, and cannot be imported
the other way under ADR-004. `carriers` is `Vector[Any]`, not a raw primitive.
It is erased for the same reason as `Value.raw`.

```scala
/**
 * The elements of one domain sort, sorted by the ordering the consumer gives for the sort's
 * carrier. Evaluation walks the elements in this order, and sampling selects by position in it, so
 * a result depends on the elements and not on how the collection holding them iterates (ADR-025).
 */
final case class OrderedDomain private (carriers: Vector[Any])

object OrderedDomain:

  /**
   * The distinct `carriers`, sorted by `ordering`. Requires that `ordering` ranks no two distinct
   * carriers equal, because such a pair would keep its input order.
   *
   * {{{
   * OrderedDomain.of(List("b", "a", "c", "a")).carriers == Vector("a", "b", "c")
   * }}}
   */
  def of[A](carriers: Iterable[A])(using ordering: Ordering[A]): OrderedDomain =
    val sorted = carriers.toVector.distinct.sorted
    require(
      sorted.lazyZip(sorted.drop(1)).forall((a, b) => ordering.compare(a, b) < 0),
      "OrderedDomain.of: the ordering ranks two distinct carriers equal",
    )
    new OrderedDomain(sorted)

end OrderedDomain

case class RuntimeModel(
  domains: Map[TypeId, OrderedDomain],
  dispatcher: RuntimeDispatcher):

  /** Each domain's elements as `Value`s of its sort, in the domain's order. */
  private[typed] val elements: Map[TypeId, Vector[Value]] =
    domains.map((sort, domain) => sort -> domain.carriers.map(Value(sort, _)))

  // validateAgainst is unchanged
```

### 5.2 The sampler — `core/src/main/scala/vql/sampling/Sampler.scala`, `HDRSampler.scala`, `ProportionEstimator.scala`

Every population parameter becomes `IndexedSeq[A]`. The scaladoc of each
states that the population's order fixes which elements are selected.

```scala
// Sampler.scala
trait Sampler[A]:
  def sample(population: IndexedSeq[A], n: Int): Set[A]
  def sampleWithPredicate(population: IndexedSeq[A], n: Int, predicate: A => Boolean): (Set[A], Int)

object Sampler:
  def autoSample[A : ClassTag](
    population: IndexedSeq[A],
    params: SamplingParams = SamplingParams(),
    config: HDRConfig = HDRConfig.default,
  ): (Set[A], Int)

// HDRSampler.scala
def sample(population: IndexedSeq[A], n: Int): Set[A] =
  require(n >= 0, s"Sample size must be non-negative, got $n")
  if n == 0 || population.isEmpty then Set.empty
  else if n >= population.size then population.toSet
  else sampleInternal(population, n)

private def sampleInternal(population: IndexedSeq[A], n: Int): Set[A]
// body unchanged: population.toArray, then partial Fisher-Yates

// ProportionEstimator.scala
def estimateWithSampling[A](
  population: IndexedSeq[A],
  predicate: A => Boolean,
  params: SamplingParams = SamplingParams(),
  config: HDRConfig = HDRConfig.default,
  sampler: Option[Sampler[A]],
)(using ClassTag[A]
): ProportionEstimate

def adaptiveEstimate[A](
  population: IndexedSeq[A],
  predicate: A => Boolean,
  params: SamplingParams = SamplingParams(),
  config: HDRConfig = HDRConfig.default,
  maxIterations: Int = 5,
)(using ClassTag[A]
): ProportionEstimate

def batchEstimate[A](
  population: IndexedSeq[A],
  predicates: Map[String, A => Boolean],
  params: SamplingParams = SamplingParams(),
  config: HDRConfig = HDRConfig.default,
)(using ClassTag[A]
): Map[String, ProportionEstimate]
```

`estimateWithSampling`'s `sampler: Option[Sampler[A]] = None` loses its
default. That is the boyscout rule against a defaulted `Option`. A caller with
no sampler writes `None`.

### 5.3 The evaluator — `core/src/main/scala/vql/typed/TypedSemantics.scala`

`Truth` is a new private enum. No existing type denotes a truth value that may
be unknown. `QueryError.EvaluationError` is the existing type of the refusal it
carries.

```scala
/** A formula's truth value. `Unknown` holds the dispatcher refusal that left it undecided. */
private enum Truth:
  case Known(value: Boolean)
  case Unknown(refusal: QueryError.EvaluationError)
```

The public entries keep their signatures. Their bodies read `model.elements`
in place of `model.domains`:

```scala
def evaluate(
  query: BoundQuery,
  model: RuntimeModel,
  answerTuple: Map[String, Value] = Map.empty,
  samplingParams: SamplingParams = SamplingParams.exact,
  hdrConfig: HDRConfig = HDRConfig.default,
): Either[QueryError, EvaluationOutput[Value]]

def satisfyingSet(
  formula: BoundFormula,
  variable: BoundVar,
  model: RuntimeModel,
): Either[QueryError, Set[Value]]
```

The private functions that change:

```scala
private def collectRangeElements(
  query: BoundQuery,
  model: RuntimeModel,
  baseEnv: Env,
  rootDomain: Vector[Value],
): Either[QueryError, Vector[Value]]

private def evaluateOverRange(
  query: BoundQuery,
  model: RuntimeModel,
  baseEnv: Env,
  rangeElements: Vector[Value],
  quantifier: VagueQuantifier,
  samplingParams: SamplingParams,
  hdrConfig: HDRConfig,
): Either[QueryError, EvaluationOutput[Value]]
// EvaluationOutput.rangeElements is rangeElements.toSet

/** The formula's truth value, failing with the refusal that left it unknown. */
private def holds(formula: BoundFormula, env: Env, model: RuntimeModel): Either[QueryError, Boolean] =
  evalFormula(formula, env, model).flatMap {
    case Truth.Known(value)     => Right(value)
    case Truth.Unknown(refusal) => Left(refusal)
  }

/** A dispatcher refusal becomes `Unknown`; any other failure stays a failure. */
private def classify(result: Either[QueryError, Boolean]): Either[QueryError, Truth] =
  result match
    case Right(value) => Right(Truth.Known(value))
    case Left(
          refusal @ QueryError.EvaluationError(
            EvaluationFailure.PredicateRefused(_, _) | EvaluationFailure.FunctionRefused(_, _)
          )
        ) => Right(Truth.Unknown(refusal))
    case Left(defect) => Left(defect)

private def evalFormula(formula: BoundFormula, env: Env, model: RuntimeModel): Either[QueryError, Truth] =
  formula match
    case BoundFormula.True            => Right(Truth.Known(true))
    case BoundFormula.False           => Right(Truth.Known(false))
    case BoundFormula.Atom(a)         => classify(evalAtom(a, env, model))
    case BoundFormula.Not(p)          => evalFormula(p, env, model).map(negate)
    case BoundFormula.And(p, q)       =>
      evalFormula(p, env, model).flatMap {
        case Truth.Known(false) => Right(Truth.Known(false))
        case left               => evalFormula(q, env, model).map(conjoin(left, _))
      }
    case BoundFormula.Or(p, q)        =>
      evalFormula(p, env, model).flatMap {
        case Truth.Known(true) => Right(Truth.Known(true))
        case left              => evalFormula(q, env, model).map(disjoin(left, _))
      }
    case BoundFormula.Imp(p, q)       =>
      evalFormula(p, env, model).flatMap {
        case Truth.Known(false) => Right(Truth.Known(true))
        case left               => evalFormula(q, env, model).map(disjoin(negate(left), _))
      }
    case BoundFormula.Iff(p, q)       =>
      for
        left  <- evalFormula(p, env, model)
        right <- evalFormula(q, env, model)
      yield (left, right) match
        case (Truth.Known(l), Truth.Known(r)) => Truth.Known(l == r)
        case (unknown: Truth.Unknown, _)      => unknown
        case (_, unknown: Truth.Unknown)      => unknown
    case BoundFormula.Forall(v, body) => quantify(body, v, env, model, decisive = false)
    case BoundFormula.Exists(v, body) => quantify(body, v, env, model, decisive = true)

private def negate(truth: Truth): Truth =
  truth match
    case Truth.Known(value)     => Truth.Known(!value)
    case unknown: Truth.Unknown => unknown

private def conjoin(left: Truth, right: Truth): Truth =
  (left, right) match
    case (Truth.Known(false), _) | (_, Truth.Known(false)) => Truth.Known(false)
    case (Truth.Known(true), _)                            => right
    case (unknown: Truth.Unknown, _)                       => unknown

private def disjoin(left: Truth, right: Truth): Truth =
  (left, right) match
    case (Truth.Known(true), _) | (_, Truth.Known(true)) => Truth.Known(true)
    case (Truth.Known(false), _)                         => right
    case (unknown: Truth.Unknown, _)                     => unknown

/**
 * `∃` when `decisive` is true and `∀` when it is false. The first binding whose body is
 * `Known(decisive)` decides. Otherwise the result is the first `Unknown` met, else
 * `Known(!decisive)`.
 */
private def quantify(
  body: BoundFormula,
  variable: BoundVar,
  env: Env,
  model: RuntimeModel,
  decisive: Boolean,
): Either[QueryError, Truth] =
  model.elements.get(variable.sort) match
    case None         =>
      Left(QueryError.DomainNotFoundError(typeName = variable.sort, availableTypes = model.domains.keySet))
    case Some(domain) =>
      @tailrec
      def walk(remaining: List[Value], firstUnknown: Option[Truth.Unknown]): Either[QueryError, Truth] =
        remaining match
          case Nil           => Right(firstUnknown.getOrElse(Truth.Known(!decisive)))
          case value :: rest =>
            evalFormula(body, env + (variable.name -> value), model) match
              case Right(Truth.Known(b)) if b == decisive => Right(Truth.Known(decisive))
              case Right(Truth.Known(_))                  => walk(rest, firstUnknown)
              case Right(unknown: Truth.Unknown)          => walk(rest, firstUnknown.orElse(Some(unknown)))
              case Left(defect)                           => Left(defect)
      walk(domain.toList, None)
```

`collectRangeElements`, `collectSatisfyingSample` and `satisfyingSet` call
`holds` where they call `evalFormula` today. `evalAtom`, `evalTerms` and
`evalTerm` are unchanged.

**Worked example.** The domain holds `a1` and `a2`. The dispatcher gives
`r(a1) = true`, `r(a2) = false`, `q(a1) = true`, and refuses `q(a2)`.

| Scope | `a1` | `a2` | Today | After |
|---|---|---|---|---|
| `r(x) ∧ q(x)` | T ∧ T = T | F ∧ U = F | satisfied by {`a1`} | satisfied by {`a1`} |
| `q(x) ∧ r(x)` | T ∧ T = T | U ∧ F = F | fails with `q`'s refusal | satisfied by {`a1`} |
| `r(x) ∨ q(x)` | T ∨ T = T | F ∨ U = U | fails with `q`'s refusal | fails with `q`'s refusal |

The third row still fails, because no value of `q(a2)` would leave the answer
unchanged.

---

## 6. Implementation phases

Each phase compiles and passes the full suite on both platforms before the next
starts. The plan ships whole, as one release.

**Phase 1 — the ordered domain and the sampler.**
1. `vql/typed/RuntimeModel.scala` (§5.1).
2. `vql/sampling/Sampler.scala`, `HDRSampler.scala` and
   `ProportionEstimator.scala` (§5.2). `adaptiveEstimate` already passes
   `Some(sampler)`.
3. `vql/typed/TypedSemantics.scala`: `evaluate`, `satisfyingSet`,
   `collectRangeElements` and `evaluateOverRange` read `model.elements`
   (§5.3, the first two code blocks). `allOf` and `anyOf` walk
   `model.elements`. The Kleene change waits for Phase 2.
4. Every `RuntimeModel` construction builds its domains with
   `OrderedDomain.of`:
   - `core/src/main/scala/examples/VagueDemo.scala`;
   - `core/src/test/scala/vql/semantics/VagueSemanticsTypedSpec.scala`;
   - `core/src/test/scala/vql/semantics/CompoundRangeE2ESpec.scala`;
   - `core/src/test/scala/vql/semantics/SatisfyingSetSpec.scala`;
   - `core/src/test/scala/vql/typed/MapDispatcherSpec.scala`;
   - `core/src/test/scala/vql/typed/CompoundRangeSpec.scala`;
   - `core/.jvm/src/test/scala/parser/NestingBoundStackSpec.scala`.

   Each test keeps its assertions. Only the domain's construction changes.
5. The sampling tests pass a `Vector` where they pass a `Set` today, and keep
   their assertions: `FisherYatesHDRSpec`, `HDRSamplerSpec`, `SamplingSpec`
   and `VagueQuantifierSpec`. One exception was ruled by the user on
   2026-11-15. `SamplingSpec`'s "end-to-end sampling pipeline" asserted that a
   single seed's estimate lies in 0.4 to 0.6. Under the sorted population
   that seed gives 0.395, a draw in the 5.2% tail the hypergeometric
   distribution assigns to that range. The test is replaced by two:
   - a chi-squared goodness-of-fit test of the satisfying count over 1 000
     entity IDs against the exact hypergeometric distribution, at α = 0.001,
     with outcomes grouped to an expected count of at least 5 and the
     critical value from the Wilson–Hilferty approximation;
   - a pinned test: `seed3 = 123` over the sorted numbers 1 to 500 samples 81
     and finds 32 satisfying, and the interval contains 0.5.
6. `core/src/test/scala/vql/typed/OrderedDomainSpec.scala`, new, with these
   tests:
   - `of(List("b", "a", "c", "a")).carriers` is `Vector("a", "b", "c")`.
   - An `Ordering` that ranks two distinct carriers equal throws
     `IllegalArgumentException`. The test uses `Ordering.by[String, Int](_.length)`
     on `List("ab", "cd")`.
   - A `RuntimeModel`'s `elements` for a sort are `Value`s of that sort in the
     domain's order.
7. `HDRSamplerSpec` gains one test. A class `Handle(key: String)` that does not
   override `equals` or `hashCode` has an identity `hashCode`. Two lists of 200
   fresh `Handle`s with the same keys, one reversed, each go through
   `OrderedDomain.of` with `Ordering.by(_.key)`. Sampling 50 from each sorted
   vector with the same `HDRConfig` selects the same keys.

**Phase 2 — the three-valued evaluator.**
1. `vql/typed/TypedSemantics.scala`, the rest of §5.3: `Truth`, `holds`,
   `classify`, `evalFormula`, `negate`, `conjoin`, `disjoin` and `quantify`.
   `allOf` and `anyOf` are removed, because `quantify` replaces them.
2. `core/src/test/scala/vql/typed/KleeneEvaluationSpec.scala`, new. It uses
   §5.3's worked-example model through `TypedSemantics.evaluate` with
   `SamplingParams.exact`. Its tests:
   - each row of §5.3's worked example gives the result in its "After" column;
   - `p ∧ q` equals `q ∧ p`, and `p ∨ q` equals `q ∨ p`, for each pair drawn
     from `r(x)`, `q(x)` and their negations;
   - `r(x) → q(x)` is satisfied by both elements, because `r(a2)` is false;
   - `r(x) ↔ q(x)` fails with `q`'s refusal;
   - the scope `∃y . q(y)` is satisfied by both elements. `∀y . q(y)` fails
     with `q`'s refusal. `∀y . ¬q(y)` is satisfied by neither, because
     `q(a1)` is true. Each holds with the domain built in either input order;
   - a function refusal inside an atom makes the atom unknown. The model adds
     a function `f` that refuses `a2` and a predicate `s` that is true. Then
     `s(f(x)) ∧ r(x)` is false on `a2`;
   - `VariableSortMismatch` still fails at once. The scope `t(x) ∨ True`,
     where the atom's `BoundVar` carries a sort other than the bound value's,
     fails with `VariableSortMismatch`, although `True` would decide.
3. The existing short-circuit tests in `VagueSemanticsTypedSpec`, "And
   short-circuits", "Or short-circuits" and "Imp short-circuits", keep their
   assertions. The sentinel stays undispatched, because the left operand is
   known and decides. The two refusal tests in `MapDispatcherSpec` keep their
   assertions, because each refused atom is the whole scope.

**Phase 3 — the pipeline, records and documents.**
1. `VagueSemanticsTypedSpec`, end to end through `VagueQueryParser.parse`,
   `FolModel`, `VagueSemantics.evaluateTyped` and
   `VagueSemantics.satisfyingSet`. The catalog declares `DomainType(asset)` and
   the predicates `r: (asset)` and `q: (asset)`. The dispatcher is §5.3's
   worked example.
   - `Q[>=]^{1/2} x (r(x) \/ ~r(x), q(x) /\ r(x))` returns `Right` with
     satisfying elements {`a1`}.
   - The same query with scope `r(x) /\ q(x)` returns the same result.
   - With scope `r(x) \/ q(x)` it returns `Left(QueryError.EvaluationError(PredicateRefused(q, …)))`.
   - `satisfyingSet` of `q(x) /\ r(x)` returns {`a1`}.
2. `VagueSemanticsTypedSpec`, sampled end to end. A domain of 200 `Handle`s,
   built twice from fresh objects in opposite orders, with a total dispatcher
   and `SamplingParams()`. `evaluateTyped` returns the same satisfying keys
   for both models. The sample size is below 200, so the sampler selects.
3. `docs/ADR-025.md`, new, "Order-Independent Evaluation", following
   `docs/ADR-00X.md`:
   - **Context:** a `Set` iterates in `hashCode` order; a dispatcher may
     refuse; left-first evaluation makes a result depend on operand order; a
     sample selected by position depends on the population's order.
   - **Decision:** 1. a dispatcher refusal is an unknown truth value, under
     §4.1's tables; 2. only dispatcher refusals are unknown; 3. an unknown
     range or scope fails with its refusal; 4. a domain holds its elements in
     the consumer's order, via `OrderedDomain.of`; 5. evaluation and sampling
     read that order; 6. a new connective, quantifier, domain walk or
     sampling entry follows 1 and 5.
   - **Code smells:** passing on a refusal before the other operand is
     evaluated; sampling from a `Set`; an `Ordering` that ranks distinct
     carriers equal.
   - **Alternatives rejected:** strict evaluation, which fails on every
     refusal and evaluates every operand on every model; documenting totality
     as a precondition, which keeps both order dependences; `OIE-D-1`'s
     unchosen option.
   - **Cross-ADR:** ADR-017 and ADR-003, the two Layer 3 rows.
   - **Implementation table:** `vql/typed/RuntimeModel.scala`,
     `vql/typed/TypedSemantics.scala`, `vql/sampling/HDRSampler.scala`,
     `vql/sampling/Sampler.scala`, `vql/sampling/ProportionEstimator.scala`.
4. `docs/ADR-017.md` §7: the bullets beginning "Range extraction
   short-circuits exactly as scope evaluation does" and "An inner range
   quantifier" are replaced by one bullet: "A dispatcher refusal is an unknown
   truth value, so a range formula fails on an element only when the refusal
   could change that element's membership. Range extraction walks the domain
   in its declared order (ADR-025)." The cross-ADR section gains ADR-025.
5. `docs/ADR-003.md`:
   - §2's code block becomes §5.2's `sample`.
   - §2 gains: "The selection is a function of the population's order, so the
     population is an `IndexedSeq`. The typed pipeline passes a range in its
     domain's declared order (ADR-025)."
   - The cross-ADR content gains ADR-025.
6. `docs/ADR-001.md` §4: the code block's `domains: Map[TypeId, Set[Value]]`
   becomes `domains: Map[TypeId, OrderedDomain]`.
7. `.claude/skills/vql-adr-constraints/SKILL.md`:
   - Layer 2 gains the ADR-025 row.
   - Layer 1, "Changing evaluation, the runtime model or the dispatcher",
     gains ADR-025 in its always column.
   - Layer 1, "Changing sampling or estimation", gains ADR-025 in its always
     column.
   - Layer 3 gains the rows ADR-025 × ADR-017 and ADR-025 × ADR-003.
   - Layer 4's row "ADR-001, ADR-015 | ADR-028 §1–§2, §4" gains the appendix
     table's "Domain" row and the domain's type.
8. `docs/Architecture.md`: the package map's `vql/typed` entry gains
   `OrderedDomain`. The sentence "`RuntimeModel` supplies each sort's domain"
   gains "in the order the consumer declares". The record table gains ADR-025.
9. `README.md`: lines 37 and 49 build the domain with `OrderedDomain.of`, and
   the import on line 23 gains `OrderedDomain`.
10. `docs/VagueQuantifiers.md` line 183: `sample(population, n): Set[A]`
    becomes `sample(population: IndexedSeq[A], n): Set[A]`.
11. `CHANGELOG.md`, 0.21.0:
    - "Changed (breaking)": `RuntimeModel.domains` holds `OrderedDomain`;
      every sampling entry takes `IndexedSeq`;
      `ProportionEstimator.estimateWithSampling`'s `sampler` has no default; a
      dispatcher refusal fails a formula only when it could change the result.
    - "Added": `OrderedDomain`.
    - "Fixed": a result no longer depends on operand order or on a `Set`'s
      iteration order.
12. `docs/TODOS.md`: T-009's status becomes "RESOLVED by
    `PLAN-order-independent-evaluation` (ADR-025)", and T-010's the same.
13. The register task-list entry of §10, in the repository `register`.
14. `build.sbt`: the user runs
    `sed -i 's/^ThisBuild \/ version      := .*/ThisBuild \/ version      := "0.21.0"/' build.sbt`.
    The user runs the release per `docs/RELEASE.md`.

---

## 7. Verification plan

The tests added or changed are listed in §6. These commands must be green:

```bash
sbt "folEngineJVM/testOnly vql.typed.OrderedDomainSpec vql.typed.KleeneEvaluationSpec vql.sampling.HDRSamplerSpec vql.semantics.VagueSemanticsTypedSpec"
# expected: ends in [success]
sbt test
# expected: ends in [success]; both platforms
```

Phase 3 items 1 and 2 are the end-to-end tests `docs/WORKING-INSTRUCTIONS.md`
requires for a public surface change. They reach the change through
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
- `core/src/main/scala/vql/typed/TypedSemantics.scala`
- `core/src/main/scala/vql/sampling/Sampler.scala`
- `core/src/main/scala/vql/sampling/HDRSampler.scala`
- `core/src/main/scala/vql/sampling/ProportionEstimator.scala`
- `core/src/main/scala/examples/VagueDemo.scala`
- `core/src/test/scala/vql/typed/OrderedDomainSpec.scala` (new)
- `core/src/test/scala/vql/typed/KleeneEvaluationSpec.scala` (new)
- `core/src/test/scala/vql/typed/MapDispatcherSpec.scala`
- `core/src/test/scala/vql/typed/CompoundRangeSpec.scala`
- `core/src/test/scala/vql/semantics/VagueSemanticsTypedSpec.scala`
- `core/src/test/scala/vql/semantics/CompoundRangeE2ESpec.scala`
- `core/src/test/scala/vql/semantics/SatisfyingSetSpec.scala`
- `core/src/test/scala/vql/sampling/FisherYatesHDRSpec.scala`
- `core/src/test/scala/vql/sampling/HDRSamplerSpec.scala`
- `core/src/test/scala/vql/sampling/SamplingSpec.scala`
- `core/src/test/scala/vql/quantifier/VagueQuantifierSpec.scala`
- `core/.jvm/src/test/scala/parser/NestingBoundStackSpec.scala`
- `docs/ADR-025.md` (new)
- `docs/ADR-017.md`
- `docs/ADR-003.md`
- `docs/ADR-001.md`
- `.claude/skills/vql-adr-constraints/SKILL.md`
- `docs/Architecture.md`
- `docs/VagueQuantifiers.md`
- `README.md`
- `CHANGELOG.md`
- `docs/TODOS.md`
- `build.sbt` (the version line, run by the user)

Outside this repository, one file in `register`, written once in Phase 3:
`docs/dev/TODO.md`.

---

## 9. Consumer-document impact

- `README.md` lines 37 and 49 build a domain as `Set[Value]`. Stale; Phase 3
  item 9.
- `docs/VagueQuantifiers.md` line 183 states the sampler's `Set` signature.
  Stale; Phase 3 item 10.
- `docs/Architecture.md` line 101, "`RuntimeModel` supplies each sort's
  domain", and its package map. Stale; Phase 3 item 8.
- Scaladoc: `RuntimeModel`, `Sampler`, `HDRSampler`, the three
  `ProportionEstimator` entries and `TypedSemantics.satisfyingSet`. Brought to
  state in Phases 1 and 2. `HDRSampler`'s "Reproducible: Same across all
  platforms and languages" gains "for a population in the same order".

---

## 10. What register must do, named once

This is written into `register/docs/dev/TODO.md` as one item in Phase 3.

- `build.sbt` line 40: `vqlEngineVersion` to `0.21.0`.
- `modules/server/.../foladapter/RiskTreeKnowledgeBase.scala`: `nodeDomain` and
  `mitigationDomain` (around line 289) become `OrderedDomain.of(…)` over
  `NodeId` and `MitigationId`, each with `Ordering.by(_.value)`.
- A query whose scope or range meets a dispatcher refusal that cannot change
  its result now returns a result in place of `EvaluationError`. Register's
  refusals, such as `lookupResult`'s "no simulation result for node", reach the
  user only when they decide the answer.
- `docs/dev/decision-records/ADR-028-appendix-technical-design.md` line 19 and
  `ADR-028-vague-quantifier-query-pane.md` line 54 state the domain as a
  `Set[Value]`. Register amends them.
