package vql.typed

import vql.error.RuntimeModelError

/**
 * A sort-tagged runtime value produced by `TypedSemantics.evaluate`.
 *
 * `raw` holds the actual JVM value. Its type is erased to `Any` because the library evaluates over
 * heterogeneous sorts without knowledge of consumer JVM types. The sort correctness guarantee from
 * the bind phase (ADR-001) ensures that `raw` is always the JVM type the consumer associated with
 * `sort` at model construction time. Recover the typed view via `value.extract[A]` (ADR-015 §2,
 * `Extract` typeclass).
 */
case class Value(sort: TypeId, raw: Any)

trait RuntimeDispatcher:
  def evalFunction(name: SymbolName, args: List[Value]): Either[String, Any]
  def evalPredicate(name: SymbolName, args: List[Value]): Either[String, Boolean]
  def functionSymbols: Set[SymbolName]
  def predicateSymbols: Set[SymbolName]

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

/**
 * The runtime data a catalog is evaluated against: one [[OrderedDomain]] per domain sort, and the
 * dispatcher that computes each function and predicate.
 */
case class RuntimeModel(
  domains: Map[TypeId, OrderedDomain],
  dispatcher: RuntimeDispatcher):

  /** Each domain's elements as `Value`s of its sort, in the domain's order. */
  private[typed] val elements: Map[TypeId, Vector[Value]] =
    domains.map((sort, domain) => sort -> domain.carriers.map(Value(sort, _)))

  def validateAgainst(catalog: TypeCatalog): Either[List[RuntimeModelError], RuntimeModel] =
    val missingFns     = catalog.functions.keySet
      .diff(dispatcher.functionSymbols)
      .toList
      .map(RuntimeModelError.MissingFunctionImplementation(_))
    val missingPreds   = catalog.predicates.keySet
      .diff(dispatcher.predicateSymbols)
      .toList
      .map(RuntimeModelError.MissingPredicateImplementation(_))
    val missingDomains = catalog.domainTypes
      .diff(domains.keySet)
      .toList
      .map(RuntimeModelError.MissingDomainForType(_))
    val errors         = missingFns ++ missingPreds ++ missingDomains
    if errors.isEmpty then Right(this) else Left(errors)

  end validateAgainst

end RuntimeModel
