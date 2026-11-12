package vql.error

import vql.id.TypeId

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

  /** Error during query parsing */
  case class ParseError(
    message: String,
    input: String,
    position: Option[Int] = None,
    override val context: Map[String, String] = Map.empty)
    extends QueryError:

    override def formatted: String =
      val pos     = position.map(p => s" at position $p").getOrElse("")
      val snippet = if input.length > 50 then input.take(50) + "..." else input
      s"Parse error$pos: $message\nInput: $snippet" +
        (if context.isEmpty then ""
         else "\n" + context.map { case (k, v) => s"  $k: $v" }.mkString("\n"))

  // ==================== Validation Errors ====================

  /** Validation error (well-formed but invalid query) */
  case class ValidationError(
    message: String,
    field: String,
    override val context: Map[String, String] = Map.empty)
    extends QueryError:

    override def formatted: String =
      s"Validation error in '$field': $message" +
        (if context.isEmpty then ""
         else "\n" + context.map { case (k, v) => s"  $k: $v" }.mkString("\n"))

  // ==================== Evaluation Errors ====================

  /** Error during query evaluation */
  case class EvaluationError(
    message: String,
    phase: String,
    cause: Option[Throwable] = None,
    override val context: Map[String, String] = Map.empty)
    extends QueryError:

    override def formatted: String =
      val causeMsg = cause.map(c => s"\nCause: ${c.getMessage}").getOrElse("")
      s"Evaluation error in $phase: $message$causeMsg" +
        (if context.isEmpty then ""
         else "\n" + context.map { case (k, v) => s"  $k: $v" }.mkString("\n"))

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
  case class UnknownConstantOrLiteralError(name: String) extends QueryError:
    def message          = s"Unknown constant or literal: '$name'"
    override val context = Map("constant" -> name)

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

  /** Unbound variable in valuation */
  case class UnboundVariableError(
    variableName: String,
    boundVariables: Set[String])
    extends QueryError:
    def message = s"Unbound variable: '$variableName'"

    override val context = Map(
      "variable"        -> variableName,
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
