package vql.id

/**
 * The identity of a sort declared in a `TypeCatalog`. Declared below `vql.error` and `vql.typed`
 * so both can hold it (ADR-023).
 */
opaque type TypeId = String

object TypeId:

  def apply(value: String): TypeId =
    require(value.nonEmpty, "TypeId must be non-empty")
    value

  extension (s: TypeId) def value: String = s

/** The name of a function or predicate declared in a `TypeCatalog` (ADR-023). */
opaque type SymbolName = String

object SymbolName:

  def apply(value: String): SymbolName =
    require(value.nonEmpty, "SymbolName must be non-empty")
    value

  extension (s: SymbolName) def value: String = s
