package vql.error

import vql.id.{SymbolName, TypeId}

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

/** Why `TypeCatalog.apply` refused a catalog. Validation returns every error it finds. */
enum TypeCatalogError:
  /** `typeId` is named at `site` and is not declared in the catalog's types. */
  case UnknownType(typeId: TypeId, site: DeclarationSite)

  /** `symbol` is declared both as a function and as a predicate. */
  case NameCollision(symbol: SymbolName)

  /** The one human-readable sentence for this error. */
  def message: String = this match
    case UnknownType(typeId, site) => s"unknown type '${typeId.value}' in ${site.label}"
    case NameCollision(symbol)     =>
      s"'${symbol.value}' is declared as both a function and a predicate"

end TypeCatalogError
