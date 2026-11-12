package vql.error

import vql.id.{SymbolName, TypeId}

/**
 * A gap between a catalog and the runtime model paired with it, found once when the model is
 * validated (ADR-001 §4). Validation returns every gap together rather than stopping at the first.
 */
enum RuntimeModelError:
  /** A function declared in the catalog has no implementation in the dispatcher. */
  case MissingFunctionImplementation(name: SymbolName)

  /** A predicate declared in the catalog has no implementation in the dispatcher. */
  case MissingPredicateImplementation(name: SymbolName)

  /**
   * A domain type declared in the catalog has no registered element set in the runtime model.
   * Startup validation covers both dispatcher symbols and domain registration (ADR-001 §4).
   */
  case MissingDomainForType(typeName: TypeId)

  /** The one human-readable sentence for this error. */
  def message: String = this match
    case MissingFunctionImplementation(n)  => s"missing function: ${n.value}"
    case MissingPredicateImplementation(n) => s"missing predicate: ${n.value}"
    case MissingDomainForType(t)           => s"missing domain for type: ${t.value}"

end RuntimeModelError
