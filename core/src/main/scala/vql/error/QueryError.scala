package vql.error

import lexer.{LiteralText, VariableText}
import parser.SourcePosition
import vql.id.{SymbolName, TypeId}

/**
 * Structured error types for vague quantifier evaluation.
 *
 * Encoded as a `sealed trait` with `case class` variants (not an `enum`) because each variant
 * overrides `formatted` and/or `context` with distinct body logic. An `enum` would require a
 * central match dispatch for those methods, which is less maintainable when the override bodies
 * differ structurally. Uniform variants ([[TypeCheckError]], [[RuntimeModelError]]) use `enum` per
 * ADR-006.
 *
 * This error hierarchy provides:
 *   - Type-safe error handling without exceptions
 *   - Composable error messages
 *   - Effect-system agnostic (works with Either, Try, ZIO, cats-effect)
 *   - No external dependencies (pure Scala 3)
 *
 * Usage with Either:
 * {{{
 * def parse(input: String): Either[QueryError, ParsedQuery] = ???
 * }}}
 *
 * Usage with ZIO (user's choice):
 * {{{
 * import zio.*
 * def parse(input: String): IO[QueryError, ParsedQuery] = ???
 * }}}
 *
 * Usage with cats-effect (user's choice):
 * {{{
 * import cats.effect.IO
 * def parse(input: String): IO[ParsedQuery] = ???  // Can handle QueryError
 * }}}
 */
sealed trait QueryError:
  /** Human-readable error message */
  def message: String

  /** Optional context information */
  def context: Map[String, String] = Map.empty

  /** Convert to exception for backward compatibility */
  def toThrowable: Throwable = QueryException(this)

  /** Pretty-print error with context */
  def formatted: String =
    val ctx = if context.isEmpty then ""
    else context.map { case (k, v) => s"  $k: $v" }.mkString("\n", "\n", "")
    s"$message$ctx"

end QueryError

object QueryError:

  // ==================== Parsing Errors ====================

  /** `VagueQueryParser.parse` refused the query's text. */
  case class ParseError(refusal: ParseRefusal, position: SourcePosition) extends QueryError:
    def message: String = refusal.message

    override def formatted: String = position match
      case SourcePosition.At(offset) => s"Parse error at position $offset: $message"
      case SourcePosition.Unknown    => s"Parse error: $message"

  // ==================== Validation Errors ====================

  /** A well-formed query, or the answer tuple it is evaluated with, breaks a rule of ADR-017 §4. */
  enum ValidationProblem:
    case QuantifiedVariableNotInRange(variable: VariableText, rangeVariables: Set[VariableText])

    /** `rangeVariables` are the range's free variables other than the quantified one. */
    case RangeVariablesNotAnswered(rangeVariables: Set[VariableText], answerVariables: List[VariableText])
    case MissingAnswerValue(variable: VariableText)
    case AnswerSortMismatch(variable: VariableText, expected: TypeId, actual: TypeId)

    /** The part of the query or its input the problem lies in. */
    def field: String = this match
      case QuantifiedVariableNotInRange(_, _) => "quantified_variable"
      case RangeVariablesNotAnswered(_, _)    => "answer_variables"
      case MissingAnswerValue(_)              => "answer_tuple"
      case AnswerSortMismatch(_, _, _)        => "answer_tuple"

    def message: String = this match
      case QuantifiedVariableNotInRange(variable, _)                =>
        s"Quantified variable '${variable.value}' must occur free in the range formula"
      case RangeVariablesNotAnswered(rangeVariables, answerVariables) =>
        s"Range variables ${rangeVariables.map(_.value).mkString(",")} must be subset of answer variables ${answerVariables.map(_.value).mkString(",")}"
      case MissingAnswerValue(variable)                             =>
        s"Missing answer tuple value for '${variable.value}'"
      case AnswerSortMismatch(variable, expected, actual)           =>
        s"Type mismatch for answer variable '${variable.value}': expected ${expected.value}, actual ${actual.value}"

  end ValidationProblem

  /** Validation error (well-formed but invalid query, or an answer tuple that does not fit it) */
  case class ValidationError(problem: ValidationProblem, position: SourcePosition) extends QueryError:
    def message: String = problem.message

    override def formatted: String = s"Validation error in '${problem.field}': $message"

  // ==================== Evaluation Errors ====================

  /** Why evaluation of a bound query stopped. */
  enum EvaluationFailure:
    /** The dispatcher refused a predicate; `dispatcherMessage` is its own text. */
    case PredicateRefused(name: SymbolName, dispatcherMessage: String)

    /** The dispatcher refused a function; `dispatcherMessage` is its own text. */
    case FunctionRefused(name: SymbolName, dispatcherMessage: String)
    case VariableSortMismatch(variable: VariableText, expected: TypeId, actual: TypeId)

    /** The step of evaluation that stopped. */
    def phase: String = this match
      case PredicateRefused(name, _)     => s"predicate:${name.value}"
      case FunctionRefused(name, _)      => s"function:${name.value}"
      case VariableSortMismatch(_, _, _) => "typed_term"

    def message: String = this match
      case PredicateRefused(_, dispatcherMessage)           => dispatcherMessage
      case FunctionRefused(_, dispatcherMessage)            => dispatcherMessage
      case VariableSortMismatch(variable, expected, actual) =>
        s"Variable '${variable.value}' has type ${actual.value}, expected ${expected.value}"

  end EvaluationFailure

  /** Error during query evaluation */
  case class EvaluationError(failure: EvaluationFailure) extends QueryError:
    def message: String = failure.message

    override def formatted: String = s"Evaluation error in ${failure.phase}: $message"

  // ==================== FOL Semantics Errors ====================

  /**
   * Defensive fallback raised when evaluation reaches a type with no registered domain, despite
   * passing binding. In a correctly wired system this should not be reachable:
   * [[vql.typed.RuntimeModel.validateAgainst]] enforces domain coverage before queries are served.
   * See also: [[vql.typed.TypedSemantics]]
   */
  case class DomainNotFoundError(
    typeName: TypeId,
    availableTypes: Set[TypeId])
    extends QueryError:
    def message = s"No domain found for type '${typeName.value}'"

    override val context = Map(
      "type"      -> typeName.value,
      "available" -> availableTypes.map(_.value).mkString(", "),
    )

    override def formatted: String =
      s"No domain found for type '${typeName.value}'. " +
        s"Available types with domains: ${availableTypes.map(_.value).mkString(", ")}"

  /**
   * Raised during the typed bind phase when a constant or literal token is not found in the
   * TypeCatalog: the name is neither a registered constant nor does a literal validator exist for
   * the expected sort. Indicates a user query error — maps to HTTP 400 UNKNOWN_REFERENCE.
   *
   * Distinguished from [[BindError]] (which covers arity/type mismatches) so callers can surface
   * targeted "did you mean …" guidance.
   */
  case class UnknownConstantOrLiteralError(name: LiteralText) extends QueryError:
    def message          = s"Unknown constant or literal: '${name.value}'"
    override val context = Map("constant" -> name.value)

  /**
   * Raised when a query fails the typed bind phase (type-check errors). Indicates a user query
   * error — all instances map to HTTP 400.
   *
   * Carries the binder's [[TypeCheckError]] values unchanged, every one the binder accumulated.
   * `messages` derives the human-readable string list from them, so the two never drift.
   */
  case class BindError(
    details: List[TypeCheckError])
    extends QueryError:
    def messages: List[String] = details.map(_.message)
    def message                = s"Query type-checking failed: ${messages.mkString("; ")}"
    override val context       = Map("errors" -> messages.mkString("; "))

  /**
   * Raised when `RuntimeModel.validateAgainst` fails (dispatcher or domain coverage gaps).
   * Indicates a wiring error, not a query error. Carries every [[RuntimeModelError]] found;
   * `errors` derives their sentences.
   */
  case class ModelValidationError(
    details: List[RuntimeModelError])
    extends QueryError:
    def errors: List[String] = details.map(_.message)
    def message          = s"Runtime model validation failed: ${errors.mkString("; ")}"
    override val context = Map("errors" -> errors.mkString("; "))

  /**
   * Unbound variable in valuation. `boundVariables` holds the names bound at that point, which
   * include the keys of the caller's answer tuple, so they are any text the caller chose.
   */
  case class UnboundVariableError(
    variableName: VariableText,
    boundVariables: Set[String])
    extends QueryError:
    def message = s"Unbound variable: '${variableName.value}'"

    override val context = Map(
      "variable"        -> variableName.value,
      "bound_variables" -> boundVariables.mkString(", "),
    )

end QueryError

/**
 * Exception wrapper for backward compatibility
 *
 * Allows throwing QueryError as exception when needed, but encourages use of Either/IO instead.
 */
case class QueryException(error: QueryError) extends Exception(error.formatted):
  def getError: QueryError = error
