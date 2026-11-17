package vql.typed

import TypeDecl.*
import munit.FunSuite
import vql.error.{DeclarationSite, SymbolKind, TypeCatalogError}

class TypeCatalogSpec extends FunSuite:

  test("TypeId and SymbolName under vql.typed are the types declared in vql.id"):
    val sort: vql.id.TypeId       = TypeId("Asset")
    val symbol: vql.id.SymbolName = SymbolName("p95")
    val back: TypeId              = vql.id.TypeId("Asset")
    assertEquals(sort, back)
    assertEquals(sort.value, "Asset")
    assertEquals(symbol.value, "p95")

  test("validate succeeds for coherent catalog"):
    val asset = TypeId("Asset")
    val loss  = TypeId("Loss")
    val bool  = TypeId("Bool")

    val result = TypeCatalog(
      types = Set(DomainType(asset), ValueType(loss), DomainType(bool)),
      constants = Map("rootAsset" -> asset),
      functions = Map(
        SymbolName("p95") -> FunctionSig(List(asset), loss)
      ),
      predicates = Map(
        SymbolName("gt_loss") -> PredicateSig(List(loss, loss))
      ),
      literalValidators = Map(loss -> (s => s.toLongOption)),
    )

    assert(result.isRight)

  test("validate fails when symbol name is reused across functions and predicates"):
    val asset = TypeId("Asset")
    val loss  = TypeId("Loss")

    val result = TypeCatalog(
      types = Set(DomainType(asset), DomainType(loss)),
      constants = Map.empty,
      functions = Map(SymbolName("foo") -> FunctionSig(List(asset), loss)),
      predicates = Map(SymbolName("foo") -> PredicateSig(List(loss))),
    )

    assert(result.isLeft)

  test("validate succeeds when loss is declared as a ValueType (not quantifiable)"):
    val asset = TypeId("Asset")
    val loss  = TypeId("Loss")

    val result = TypeCatalog(
      types = Set(DomainType(asset), ValueType(loss)), // Loss present but not quantifiable
      predicates = Map(
        SymbolName("leaf")    -> PredicateSig(List(asset)),
        SymbolName("hasloss") -> PredicateSig(List(loss)),
      ),
    )

    assert(result.isRight)

  test("domainTypes returns only DomainType ids"):
    val asset = TypeId("Asset")
    val loss  = TypeId("Loss")

    val catalog = TypeCatalog(
      types = Set(DomainType(asset), ValueType(loss)),
      predicates = Map(
        SymbolName("leaf")    -> PredicateSig(List(asset)),
        SymbolName("hasloss") -> PredicateSig(List(loss)),
      ),
    ).getOrElse(fail("catalog must be valid"))

    assertEquals(catalog.domainTypes, Set(asset))
    assertEquals(catalog.typeIds, Set(asset, loss))

  // Name-collision detection must not be sensitive to the shape of the
  // validator map (ADR-015 §4).
  test("nameCollisions detected even when validators are present"):
    val asset = TypeId("Asset")
    val loss  = TypeId("Loss")

    val result = TypeCatalog(
      types = Set(DomainType(asset), ValueType(loss)),
      functions = Map(SymbolName("foo") -> FunctionSig(List(asset), loss)),
      predicates = Map(SymbolName("foo") -> PredicateSig(List(loss))),
      literalValidators = Map(loss -> (s => s.toLongOption.map(_ => s))),
    )

    assert(result.isLeft)
    assert(result.left.exists(_.contains(TypeCatalogError.NameCollision(SymbolName("foo")))))

  // Validators may produce any consumer carrier (ADR-015 §4); e.g. parse
  // Long literals into Long carriers.
  test("validator returning Some(parsedValue: Any) is accepted"):
    val asset = TypeId("Asset")
    val loss  = TypeId("Loss")

    val result = TypeCatalog(
      types = Set(DomainType(asset), ValueType(loss)),
      literalValidators = Map(loss -> LiteralParser.asValidator[Long]),
    )

    assert(result.isRight)

  test("UnknownType names the function return site"):
    val asset = TypeId("Asset")
    val ghost = TypeId("Ghost")

    TypeCatalog(
      types = Set(DomainType(asset)),
      functions = Map(SymbolName("p95") -> FunctionSig(List(asset), ghost)),
    ) match
      case Left(List(TypeCatalogError.UnknownType(typeId, site))) =>
        assertEquals(typeId, ghost)
        assertEquals(site, DeclarationSite.FunctionReturn(SymbolName("p95")))
        assertEquals(site.label, "function 'p95' return type")
      case other                                                    =>
        fail(s"expected exactly one UnknownType, got $other")

  test("UnknownType emitted separately per site for same unknown type"):
    val asset = TypeId("Asset")
    val ghost = TypeId("Ghost")

    TypeCatalog(
      types = Set(DomainType(asset)),
      functions = Map(SymbolName("f") -> FunctionSig(List(ghost), asset)),
      predicates = Map(SymbolName("p") -> PredicateSig(List(ghost))),
    ) match
      case Left(errors) =>
        assertEquals(
          errors.toSet,
          Set(
            TypeCatalogError.UnknownType(
              ghost,
              DeclarationSite.Parameter(SymbolName("f"), SymbolKind.Function, 0),
            ),
            TypeCatalogError.UnknownType(
              ghost,
              DeclarationSite.Parameter(SymbolName("p"), SymbolKind.Predicate, 0),
            ),
          ),
        )
        assertEquals(errors.length, 2, s"expected one error per site, not deduplicated: $errors")
      case Right(_)     => fail("expected Left")
    end match

  test("UnknownType names the constant and literal-validator sites"):
    val asset = TypeId("Asset")
    val ghost = TypeId("Ghost")

    TypeCatalog(
      types = Set(DomainType(asset)),
      constants = Map("root" -> ghost),
      literalValidators = Map(ghost -> (s => Some(s))),
    ) match
      case Left(errors) =>
        assertEquals(
          errors.toSet,
          Set(
            TypeCatalogError.UnknownType(ghost, DeclarationSite.Constant("root")),
            TypeCatalogError.UnknownType(ghost, DeclarationSite.LiteralValidator),
          ),
        )
      case Right(_)     => fail("expected Left")
    end match

  test("a function may declare a DomainType return sort"):
    val asset = TypeId("Asset")
    val node  = TypeId("Node")

    val result = TypeCatalog(
      types = Set(DomainType(asset), DomainType(node)),
      functions = Map(SymbolName("node_id") -> FunctionSig(List(asset), node)),
    )
    assert(result.isRight, s"DomainType return should be accepted, got $result")

  test("a function may declare a ValueType return sort"):
    val asset = TypeId("Asset")
    val loss  = TypeId("Loss")

    val result = TypeCatalog(
      types = Set(DomainType(asset), ValueType(loss)),
      functions = Map(SymbolName("p95") -> FunctionSig(List(asset), loss)),
    )
    assert(result.isRight, s"ValueType return should be accepted, got $result")

  test("domainReturningFunctions holds exactly the functions returning a DomainType"):
    val asset   = TypeId("Asset")
    val loss    = TypeId("Loss")
    val catalog = TypeCatalog
      .unsafe(
        types = Set(DomainType(asset), ValueType(loss)),
        functions = Map(
          SymbolName("node_id") -> FunctionSig(List(loss), asset),
          SymbolName("p95")     -> FunctionSig(List(asset), loss),
        ),
      )

    assertEquals(catalog.domainReturningFunctions, Set(SymbolName("node_id")))

  test("UnknownType's message names the type and each declaration site"):
    val ghost = TypeId("Ghost")
    val cases = List(
      DeclarationSite.Constant("root") -> "unknown type 'Ghost' in constant 'root'",
      DeclarationSite.Parameter(SymbolName("f"), SymbolKind.Function, 0) ->
        "unknown type 'Ghost' in function 'f' parameter 0",
      DeclarationSite.Parameter(SymbolName("p"), SymbolKind.Predicate, 1) ->
        "unknown type 'Ghost' in predicate 'p' parameter 1",
      DeclarationSite.FunctionReturn(SymbolName("p95")) ->
        "unknown type 'Ghost' in function 'p95' return type",
      DeclarationSite.LiteralValidator -> "unknown type 'Ghost' in literal validator",
    )
    cases.foreach { (site, expected) =>
      assertEquals(TypeCatalogError.UnknownType(ghost, site).message, expected)
    }

  test("NameCollision's message names the symbol"):
    assertEquals(
      TypeCatalogError.NameCollision(SymbolName("foo")).message,
      "'foo' is declared as both a function and a predicate",
    )

  test("unsafe reports each refused declaration by its message"):
    val asset = TypeId("Asset")
    val ghost = TypeId("Ghost")

    val thrown = intercept[IllegalArgumentException](
      TypeCatalog.unsafe(
        types = Set(DomainType(asset)),
        functions = Map(SymbolName("p95") -> FunctionSig(List(asset), ghost)),
      )
    )
    assertEquals(
      thrown.getMessage,
      "Invalid TypeCatalog: unknown type 'Ghost' in function 'p95' return type",
    )

  test("unsafe joins several refused declarations' messages with a semicolon"):
    val asset = TypeId("Asset")
    val ghost = TypeId("Ghost")

    val thrown = intercept[IllegalArgumentException](
      TypeCatalog.unsafe(
        types = Set(DomainType(asset)),
        functions = Map(
          SymbolName("p95") -> FunctionSig(List(asset), ghost),
          SymbolName("foo") -> FunctionSig(List(asset), asset),
        ),
        predicates = Map(SymbolName("foo") -> PredicateSig(List(asset))),
      )
    )
    assertEquals(
      thrown.getMessage,
      "Invalid TypeCatalog: unknown type 'Ghost' in function 'p95' return type; " +
        "'foo' is declared as both a function and a predicate",
    )

end TypeCatalogSpec
