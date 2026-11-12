package vql.error

import vql.id.TypeId

/** Why `QueryBinder` refused a query or a satisfying-set formula. */
enum TypeCheckError:
  case UnknownPredicate(name: String)
  case UnknownFunction(name: String)
  case ArityMismatch(symbol: String, expected: Int, actual: Int)
  case UnknownConstantOrLiteral(name: String)
  case TypeMismatch(expected: TypeId, actual: TypeId, context: String)
  case UnboundAnswerVar(name: String)
  case UnconstrainedVar(name: String)
  case ConflictingTypes(name: String, left: TypeId, right: TypeId)

  /**
   * A constant token had a registered literal validator for its expected sort, but the validator
   * returned `None` for the source text. Distinct from [[UnknownConstantOrLiteral]], which signals
   * the absence of *any* mapping (no constant, no validator). See ADR-015 §4.
   */
  case UnparseableConstant(name: String, sort: TypeId, sourceText: String)

  /**
   * The quantified variable resolves to a sort that is not one of the catalog's domain types. The
   * sort is structurally valid; it is a value type (scalar attribute) that cannot be iterated over.
   * See ADR-014 §2.
   */
  case TypeNotQuantifiable(sort: TypeId)

  /**
   * A satisfying-set formula bound a free variable other than the single variable the set is
   * evaluated over. The formula's free variables must be exactly that variable (ADR-017 §6).
   */
  case UnexpectedFreeVar(name: String)

  /** The one human-readable sentence for this error. */
  def message: String = this match
    case UnknownPredicate(name)                     => s"unknown predicate: $name"
    case UnknownFunction(name)                      => s"unknown function: $name"
    case ArityMismatch(symbol, expected, actual)    =>
      s"arity mismatch for '$symbol': expected $expected, actual $actual"
    case UnknownConstantOrLiteral(name)             => s"unknown constant or literal: $name"
    case TypeMismatch(expected, actual, context)    =>
      s"type mismatch in $context: expected ${expected.value}, actual ${actual.value}"
    case UnboundAnswerVar(name)                     => s"unbound answer variable: $name"
    case UnconstrainedVar(name)                     => s"unconstrained quantifier variable: $name"
    case ConflictingTypes(name, left, right)        =>
      s"conflicting inferred types for '$name': ${left.value} vs ${right.value}"
    case UnparseableConstant(_, sort, sourceText)   =>
      s"cannot parse '$sourceText' as ${sort.value}"
    case TypeNotQuantifiable(sort)                  =>
      s"type '${sort.value}' is not a domain type and cannot be quantified over"
    case UnexpectedFreeVar(name)                    =>
      s"unexpected free variable '$name': a satisfying-set formula may only mention its one variable"

end TypeCheckError
