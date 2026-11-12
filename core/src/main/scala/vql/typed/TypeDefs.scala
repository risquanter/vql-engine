package vql.typed

/**
 * [[vql.id.TypeId]] under this package's path. The companion declares its result type, so an
 * inferred `TypeId(s)` is a `TypeId` and never its underlying `String`.
 */
type TypeId = vql.id.TypeId

object TypeId:
  def apply(value: String): TypeId = vql.id.TypeId(value)

/** [[vql.id.SymbolName]] under this package's path, declared as [[TypeId]] is. */
type SymbolName = vql.id.SymbolName

object SymbolName:
  def apply(value: String): SymbolName = vql.id.SymbolName(value)

/**
 * Declares a type's role in the [[TypeCatalog]].
 *
 *   - [[TypeDecl.DomainType]] — first-class entities that can be quantified over; require a
 *     registered domain in [[RuntimeModel]].
 *   - [[TypeDecl.ValueType]] — scalar / computed values; cannot be quantified over.
 *
 * Both variants carry their [[TypeId]] so the declaration is self-contained. See ADR-006 (enum
 * encoding) and ADR-014 (quantifiability semantics).
 */
enum TypeDecl:
  case DomainType(id: TypeId)
  case ValueType(id: TypeId)

  /** The type identifier carried by this declaration. */
  def typeId: TypeId = this match
    case DomainType(id) => id
    case ValueType(id)  => id
