package vql.typed

import TypeDecl.*
import vql.error.{DeclarationSite, SymbolKind, TypeCatalogError}

case class FunctionSig(params: List[TypeId], returns: TypeId)

case class PredicateSig(params: List[TypeId])

/**
 * A validated type catalog.
 *
 * Construction always runs validation. Use [[TypeCatalog.apply]] for production code (returns
 * Either). Use [[TypeCatalog.unsafe]] in tests where the catalog is known-correct.
 *
 * A TypeCatalog value is guaranteed internally consistent: all type references in signatures
 * resolve to declared types, and no symbol name appears as both a function and a predicate.
 */
case class TypeCatalog private (
  types: Set[TypeDecl],
  constants: Map[String, TypeId],
  functions: Map[SymbolName, FunctionSig],
  predicates: Map[SymbolName, PredicateSig],
  literalValidators: Map[TypeId, String => Option[Any]]):
  /** All declared type IDs, regardless of role. */
  def typeIds: Set[TypeId] = types.map(_.typeId)

  /**
   * Types that are first-class entities requiring a registered domain in [[RuntimeModel]]. Derived
   * from the [[DomainType]] tags in [[types]]. Validated by [[RuntimeModel.validateAgainst]] to
   * enforce domain coverage. See ADR-014.
   */
  def domainTypes: Set[TypeId] = types.collect { case DomainType(typeId) => typeId }

  /**
   * Functions whose declared return sort is a domain type. A consumer whose targeting fragment
   * admits exactly these applications passes this set to `Fragment.Targeting` and writes no list of
   * names.
   */
  def domainReturningFunctions: Set[SymbolName] =
    functions.collect { case (sym, sig) if domainTypes.contains(sig.returns) => sym }.toSet

end TypeCatalog

object TypeCatalog:

  /**
   * Validate and construct. Returns Left if any type reference is undeclared or any symbol name
   * collides between functions and predicates.
   *
   * @param types
   *   The type declarations. Use [[DomainType]] for types that can be quantified over and
   *   [[ValueType]] for scalar / computed types. See ADR-014.
   */
  def apply(
    types: Set[TypeDecl],
    constants: Map[String, TypeId] = Map.empty,
    functions: Map[SymbolName, FunctionSig] = Map.empty,
    predicates: Map[SymbolName, PredicateSig] = Map.empty,
    literalValidators: Map[TypeId, String => Option[Any]] = Map.empty,
  ): Either[List[TypeCatalogError], TypeCatalog] =
    collectErrors(types, constants, functions, predicates, literalValidators) match
      case Nil    => Right(new TypeCatalog(types, constants, functions, predicates, literalValidators))
      case errors => Left(errors)

  /**
   * Construct without returning Either. Throws [[IllegalArgumentException]] if validation fails,
   * and the exception's text holds each error's sentence. Use only in tests or application startup
   * where a catalog inconsistency is a programming error, not a user error.
   *
   * @param types
   *   See [[apply]] for semantics.
   */
  def unsafe(
    types: Set[TypeDecl],
    constants: Map[String, TypeId] = Map.empty,
    functions: Map[SymbolName, FunctionSig] = Map.empty,
    predicates: Map[SymbolName, PredicateSig] = Map.empty,
    literalValidators: Map[TypeId, String => Option[Any]] = Map.empty,
  ): TypeCatalog =
    apply(types, constants, functions, predicates, literalValidators)
      .fold(
        errors =>
          throw IllegalArgumentException(
            s"Invalid TypeCatalog: ${errors.map(_.message).mkString("; ")}"
          ),
        identity,
      )

  private def collectErrors(
    types: Set[TypeDecl],
    constants: Map[String, TypeId],
    functions: Map[SymbolName, FunctionSig],
    predicates: Map[SymbolName, PredicateSig],
    literalValidators: Map[TypeId, String => Option[Any]],
  ): List[TypeCatalogError] =
    val typeIds      = types.map(_.typeId)
    val unknownTypes = List.newBuilder[TypeCatalogError]

    constants.foreach { (key, s) =>
      if !typeIds.contains(s) then
        unknownTypes += TypeCatalogError.UnknownType(s, DeclarationSite.Constant(key))
    }

    functions.foreach { (sym, sig) =>
      sig.params.zipWithIndex.foreach { (s, i) =>
        if !typeIds.contains(s) then
          unknownTypes += TypeCatalogError.UnknownType(
            s,
            DeclarationSite.Parameter(sym, SymbolKind.Function, i),
          )
      }
      if !typeIds.contains(sig.returns) then
        unknownTypes += TypeCatalogError.UnknownType(
          sig.returns,
          DeclarationSite.FunctionReturn(sym),
        )
    }

    predicates.foreach { (sym, sig) =>
      sig.params.zipWithIndex.foreach { (s, i) =>
        if !typeIds.contains(s) then
          unknownTypes += TypeCatalogError.UnknownType(
            s,
            DeclarationSite.Parameter(sym, SymbolKind.Predicate, i),
          )
      }
    }

    literalValidators.keys.foreach { s =>
      if !typeIds.contains(s) then
        unknownTypes += TypeCatalogError.UnknownType(s, DeclarationSite.LiteralValidator)
    }

    val collisions =
      functions.keySet
        .intersect(predicates.keySet)
        .toList
        .map(TypeCatalogError.NameCollision(_))

    unknownTypes.result() ++ collisions

  end collectErrors

end TypeCatalog
