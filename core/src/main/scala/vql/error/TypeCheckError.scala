package vql.error

import lexer.{LiteralText, SymbolText, VariableText}
import vql.id.TypeId

/** The term whose sort did not match. */
enum MismatchSite:
  case Variable(name: VariableText)
  case Constant(name: LiteralText)
  case FunctionReturn(name: SymbolText)

  def label: String = this match
    case Variable(name)       => s"variable '${name.value}'"
    case Constant(name)       => s"constant '${name.value}'"
    case FunctionReturn(name) => s"function '${name.value}' return"

/** What kind of catalog symbol a bind error names. */
enum SymbolKind:
  case Predicate
  case Function

/** Why `QueryBinder` refused a query or a satisfying-set formula. */
enum TypeCheckError:
  case UnknownPredicate(name: SymbolText)
  case UnknownFunction(name: SymbolText)
  case ArityMismatch(symbol: SymbolText, kind: SymbolKind, expected: Int, actual: Int)
  case UnknownConstantOrLiteral(name: LiteralText)
  case TypeMismatch(expected: TypeId, actual: TypeId, site: MismatchSite)
  case UnboundAnswerVar(name: VariableText)
  case UnconstrainedVar(name: VariableText)
  case ConflictingTypes(name: VariableText, left: TypeId, right: TypeId)

  /**
   * A constant token had a registered literal validator for its expected sort, but the validator
   * returned `None` for the source text. Distinct from [[UnknownConstantOrLiteral]], which signals
   * the absence of *any* mapping (no constant, no validator). See ADR-015 §4.
   */
  case UnparseableConstant(name: LiteralText, sort: TypeId, sourceText: LiteralText)

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
  case UnexpectedFreeVar(name: VariableText)

  /** The one human-readable sentence for this error. */
  def message: String = this match
    case UnknownPredicate(name)                     => s"unknown predicate: ${name.value}"
    case UnknownFunction(name)                      => s"unknown function: ${name.value}"
    case ArityMismatch(symbol, _, expected, actual) =>
      s"arity mismatch for '${symbol.value}': expected $expected, actual $actual"
    case UnknownConstantOrLiteral(name)             => s"unknown constant or literal: ${name.value}"
    case TypeMismatch(expected, actual, site)       =>
      s"type mismatch in ${site.label}: expected ${expected.value}, actual ${actual.value}"
    case UnboundAnswerVar(name)                     => s"unbound answer variable: ${name.value}"
    case UnconstrainedVar(name)                     => s"unconstrained quantifier variable: ${name.value}"
    case ConflictingTypes(name, left, right)        =>
      s"conflicting inferred types for '${name.value}': ${left.value} vs ${right.value}"
    case UnparseableConstant(_, sort, sourceText)   =>
      s"cannot parse '${sourceText.value}' as ${sort.value}"
    case TypeNotQuantifiable(sort)                  =>
      s"type '${sort.value}' is not a domain type and cannot be quantified over"
    case UnexpectedFreeVar(name)                    =>
      s"unexpected free variable '${name.value}': a satisfying-set formula may only mention its one variable"

end TypeCheckError
