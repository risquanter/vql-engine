package vql.typed

import TypeDecl.*
import lexer.{LiteralText, SymbolText, VariableText}
import logic.{FOL, Formula, Term}
import munit.FunSuite
import vql.error.{MismatchSite, SymbolKind, TypeCheckError}
import vql.logic.ParsedQuery
import vql.quantifier.Quantifier

class QueryBinderSpec extends FunSuite:

  private val asset = TypeId("Asset")
  private val loss  = TypeId("Loss")
  private val prob  = TypeId("Probability")
  private val bool  = TypeId("Bool")

  private val catalog = TypeCatalog.unsafe(
    types = Set(DomainType(asset), ValueType(loss), ValueType(prob), ValueType(bool)),
    functions = Map(
      SymbolName("p95") -> FunctionSig(List(asset), loss),
      SymbolName("lec") -> FunctionSig(List(asset, loss), prob),
    ),
    predicates = Map(
      SymbolName("leaf")    -> PredicateSig(List(asset)),
      SymbolName("gt_loss") -> PredicateSig(List(loss, loss)),
      SymbolName("gt_prob") -> PredicateSig(List(prob, prob)),
    ),
    literalValidators = Map(
      loss -> (s => s.toLongOption),
      prob -> (s => s.toDoubleOption),
    ),
  )

  test("binds loss comparison query with unambiguous literal typing"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(2, 3),
      variable = "x",
      range = Formula.Atom(FOL("leaf", List(Term.mkVar("x")))),
      scope = Formula.Atom(
        FOL("gt_loss", List(Term.mkFn("p95", List(Term.mkVar("x"))), Term.mkConst("5000000")))
      ),
      answerVars = Nil,
    )

    val result = QueryBinder.bind(query, catalog)
    assert(result.isRight)

  test("binds probability comparison query with explicit sort-specific predicate"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "x",
      range = Formula.Atom(FOL("leaf", List(Term.mkVar("x")))),
      scope = Formula.Atom(
        FOL(
          "gt_prob",
          List(Term.mkFn("lec", List(Term.mkVar("x"), Term.mkConst("10000000"))), Term.mkConst("0.05")),
        )
      ),
      answerVars = Nil,
    )

    val result = QueryBinder.bind(query, catalog)
    assert(result.isRight)

  test("fails when literal does not validate against expected type"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "x",
      range = Formula.Atom(FOL("leaf", List(Term.mkVar("x")))),
      scope =
        Formula.Atom(FOL("gt_loss", List(Term.mkFn("p95", List(Term.mkVar("x"))), Term.mkConst("0.05")))),
      answerVars = Nil,
    )

    val result = QueryBinder.bind(query, catalog)
    assert(result.isLeft)

  test("fails with unknown symbol"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "x",
      range = Formula.Atom(FOL("unknown_range", List(Term.mkVar("x")))),
      scope = Formula.True,
      answerVars = Nil,
    )

    val result = QueryBinder.bind(query, catalog)
    assert(result.isLeft)

  // ==================== TypeNotQuantifiable tests (ADR-014 §2) ====================

  // Asset is a domain type; Loss is a value type (scalar) — not quantifiable
  private val catalogWithValueType = TypeCatalog.unsafe(
    types =
      Set(DomainType(asset), ValueType(loss)), // Loss is a value type — cannot be quantified over
    functions = Map(
      SymbolName("p95") -> FunctionSig(List(asset), loss)
    ),
    predicates = Map(
      SymbolName("leaf")    -> PredicateSig(List(asset)),
      SymbolName("gt_loss") -> PredicateSig(List(loss, loss)),
    ),
  )

  test("bind fails with TypeNotQuantifiable when root variable resolves to a value type"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "l",
      range = Formula.Atom(FOL("gt_loss", List(Term.mkVar("l"), Term.mkVar("l")))),
      scope = Formula.True,
      answerVars = Nil,
    )
    QueryBinder.bind(query, catalogWithValueType) match
      case Left(List(TypeCheckError.TypeNotQuantifiable(sort))) =>
        assertEquals(sort, TypeId("Loss"))
      case Left(other) => fail(s"Expected TypeNotQuantifiable, got $other")
      case Right(_)    => fail("Expected Left for value-type root variable")

  test("bind fails with TypeNotQuantifiable for nested Forall over value type"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "x",
      range = Formula.Atom(FOL("leaf", List(Term.mkVar("x")))),
      scope = Formula.mkForall("l", Formula.Atom(FOL("gt_loss", List(Term.mkVar("l"), Term.mkVar("l"))))),
      answerVars = Nil,
    )
    QueryBinder.bind(query, catalogWithValueType) match
      case Left(List(TypeCheckError.TypeNotQuantifiable(sort))) =>
        assertEquals(sort, TypeId("Loss"))
      case Left(other) => fail(s"Expected TypeNotQuantifiable, got $other")
      case Right(_)    => fail("Expected Left for value-type nested Forall")

  test("bind fails with TypeNotQuantifiable for nested Exists over value type"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "x",
      range = Formula.Atom(FOL("leaf", List(Term.mkVar("x")))),
      scope = Formula.mkExists("l", Formula.Atom(FOL("gt_loss", List(Term.mkVar("l"), Term.mkVar("l"))))),
      answerVars = Nil,
    )
    QueryBinder.bind(query, catalogWithValueType) match
      case Left(List(TypeCheckError.TypeNotQuantifiable(sort))) =>
        assertEquals(sort, TypeId("Loss"))
      case Left(other) => fail(s"Expected TypeNotQuantifiable, got $other")
      case Right(_)    => fail("Expected Left for value-type nested Exists")

  test("bind succeeds when root variable resolves to a domain type"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "x",
      range = Formula.Atom(FOL("leaf", List(Term.mkVar("x")))),
      scope = Formula.True,
      answerVars = Nil,
    )
    assert(QueryBinder.bind(query, catalogWithValueType).isRight)

  // ==================== Formula ranges (ADR-017) ====================

  test(
    "bind fails with ConflictingTypes when a range uses the quantified variable at incompatible sorts"
  ):
    // leaf expects Asset for x; gt_loss expects Loss for x — the merged range env conflicts.
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "x",
      range = Formula.And(
        Formula.Atom(FOL("leaf", List(Term.mkVar("x")))),
        Formula.Atom(FOL("gt_loss", List(Term.mkVar("x"), Term.mkVar("x")))),
      ),
      scope = Formula.True,
      answerVars = Nil,
    )
    QueryBinder.bind(query, catalog) match
      case Left(errs) =>
        assert(
          errs.exists(_.isInstanceOf[TypeCheckError.ConflictingTypes]),
          s"expected ConflictingTypes, got $errs",
        )
      case Right(_)   => fail("expected Left for conflicting range sorts")

  test("bind fails with TypeNotQuantifiable for an inner range quantifier over a value type"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "x",
      range = Formula.And(
        Formula.Atom(FOL("leaf", List(Term.mkVar("x")))),
        Formula.mkExists("l", Formula.Atom(FOL("gt_loss", List(Term.mkVar("l"), Term.mkVar("l"))))),
      ),
      scope = Formula.True,
      answerVars = Nil,
    )
    QueryBinder.bind(query, catalogWithValueType) match
      case Left(errs) if errs.contains(TypeCheckError.TypeNotQuantifiable(TypeId("Loss"))) => ()
      case Left(other) => fail(s"expected TypeNotQuantifiable(Loss), got $other")
      case Right(_)    => fail("expected Left for inner range quantifier over value type")

  test("bind succeeds for a sort-consistent compound range"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "x",
      range = Formula.Or(
        Formula.Atom(FOL("leaf", List(Term.mkVar("x")))),
        Formula.Not(Formula.Atom(FOL("leaf", List(Term.mkVar("x"))))),
      ),
      scope = Formula.True,
      answerVars = Nil,
    )
    assert(QueryBinder.bind(query, catalog).isRight)

  // ==================== Inline-literal binding (ADR-015 §4) ====================
  //
  // Validators carry a parsed `Any` carrier into `LiteralRef.value`. A validator
  // returning `None` produces `TypeCheckError.UnparseableConstant`. Sorts with no
  // validator fall through to `UnknownConstantOrLiteral`.

  // Catalog wired with primitive-carrier validators via LiteralParser.asValidator.
  private val catalogPrim = TypeCatalog.unsafe(
    types = Set(DomainType(asset), ValueType(loss), ValueType(prob), ValueType(bool)),
    functions = Map(
      SymbolName("p95") -> FunctionSig(List(asset), loss),
      SymbolName("lec") -> FunctionSig(List(asset, loss), prob),
    ),
    predicates = Map(
      SymbolName("leaf")    -> PredicateSig(List(asset)),
      SymbolName("gt_loss") -> PredicateSig(List(loss, loss)),
      SymbolName("gt_prob") -> PredicateSig(List(prob, prob)),
    ),
    literalValidators = Map(
      loss -> LiteralParser.asValidator[Long],
      prob -> LiteralParser.asValidator[Double],
    ),
  )

  /** Walk a bound query and find the first LiteralRef in any atom argument. */
  private def firstLiteralRef(bq: BoundQuery): BoundTerm.LiteralRef =
    def fromTerm(t: BoundTerm): Option[BoundTerm.LiteralRef]       = t match
      case c: BoundTerm.LiteralRef     => Some(c)
      case BoundTerm.FnApp(_, args, _) => args.iterator.flatMap(fromTerm).nextOption()
      case _: BoundTerm.VarRef | _: BoundTerm.ConstRef => None
    def fromFormula(f: BoundFormula): Option[BoundTerm.LiteralRef] = f match
      case BoundFormula.Atom(a)                   => a.args.iterator.flatMap(fromTerm).nextOption()
      case BoundFormula.Not(p)                    => fromFormula(p)
      case BoundFormula.And(p, q)                 => fromFormula(p).orElse(fromFormula(q))
      case BoundFormula.Or(p, q)                  => fromFormula(p).orElse(fromFormula(q))
      case BoundFormula.Imp(p, q)                 => fromFormula(p).orElse(fromFormula(q))
      case BoundFormula.Iff(p, q)                 => fromFormula(p).orElse(fromFormula(q))
      case BoundFormula.Forall(_, b)              => fromFormula(b)
      case BoundFormula.Exists(_, b)              => fromFormula(b)
      case BoundFormula.True | BoundFormula.False => None
    fromFormula(bq.scope)
      .orElse(fromFormula(bq.range))
      .getOrElse(fail("no LiteralRef found in bound query"))

  end firstLiteralRef

  test("validator parses inline literal, LiteralRef.value is parsed Any (Long)"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "x",
      range = Formula.Atom(FOL("leaf", List(Term.mkVar("x")))),
      scope = Formula.Atom(
        FOL("gt_loss", List(Term.mkFn("p95", List(Term.mkVar("x"))), Term.mkConst("5000000")))
      ),
      answerVars = Nil,
    )
    QueryBinder.bind(query, catalogPrim) match
      case Right(bq)  =>
        val cr = firstLiteralRef(bq)
        assertEquals(cr.sourceText, "5000000")
        assertEquals(cr.sort, loss)
        assertEquals(cr.value, 5000000L: Any)
        // Carrier must be the parsed primitive, NOT the source text:
        assert(
          cr.value.isInstanceOf[Long],
          s"expected Long carrier, got ${cr.value.getClass.getName}: ${cr.value}",
        )
      case Left(errs) => fail(s"expected Right, got Left($errs)")

  test("validator parses Double literal, LiteralRef.value is Double"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "x",
      range = Formula.Atom(FOL("leaf", List(Term.mkVar("x")))),
      scope = Formula.Atom(
        FOL(
          "gt_prob",
          List(Term.mkFn("lec", List(Term.mkVar("x"), Term.mkConst("10000000"))), Term.mkConst("0.05")),
        )
      ),
      answerVars = Nil,
    )
    QueryBinder.bind(query, catalogPrim) match
      case Right(bq)  =>
        // Walk the scope explicitly to grab the Double literal (2nd arg of gt_prob).
        val scope   = bq.scope match
          case BoundFormula.Atom(a) => a
          case other                => fail(s"expected Atom scope, got $other")
        val probArg = scope.args(1) match
          case c: BoundTerm.LiteralRef => c
          case other                   => fail(s"expected LiteralRef as 2nd arg, got $other")
        assertEquals(probArg.sourceText, "0.05")
        assertEquals(probArg.sort, prob)
        assertEquals(probArg.value, 0.05: Any)
        assert(probArg.value.isInstanceOf[Double])
      case Left(errs) => fail(s"expected Right, got Left($errs)")

  test("validator returns None -> UnparseableConstant(name, sort, sourceText)"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "x",
      range = Formula.Atom(FOL("leaf", List(Term.mkVar("x")))),
      // "0.05" cannot be parsed as Long for the loss sort.
      scope =
        Formula.Atom(FOL("gt_loss", List(Term.mkFn("p95", List(Term.mkVar("x"))), Term.mkConst("0.05")))),
      answerVars = Nil,
    )
    QueryBinder.bind(query, catalogPrim) match
      case Left(errs) =>
        val matched = errs.collect {
          case e @ TypeCheckError.UnparseableConstant(name, sort, src) => (name, sort, src)
        }
        assertEquals(matched, List((LiteralText("0.05"), loss, LiteralText("0.05"))))
      case Right(_)   => fail("expected Left(UnparseableConstant)")

  test("sort with no validator falls through to UnknownConstantOrLiteral"):
    // Catalog with NO validator for `loss`.
    val noValidator = TypeCatalog.unsafe(
      types = Set(DomainType(asset), ValueType(loss), ValueType(prob), ValueType(bool)),
      functions = Map(
        SymbolName("p95") -> FunctionSig(List(asset), loss)
      ),
      predicates = Map(
        SymbolName("leaf")    -> PredicateSig(List(asset)),
        SymbolName("gt_loss") -> PredicateSig(List(loss, loss)),
      ),
    )
    val query       = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "x",
      range = Formula.Atom(FOL("leaf", List(Term.mkVar("x")))),
      scope = Formula.Atom(
        FOL("gt_loss", List(Term.mkFn("p95", List(Term.mkVar("x"))), Term.mkConst("5000000")))
      ),
      answerVars = Nil,
    )
    QueryBinder.bind(query, noValidator) match
      case Left(errs) =>
        assert(
          errs.exists {
            case TypeCheckError.UnknownConstantOrLiteral(name) => name == LiteralText("5000000")
            case _                                       => false
          },
          s"expected UnknownConstantOrLiteral, got $errs",
        )
        assert(
          !errs.exists(_.isInstanceOf[TypeCheckError.UnparseableConstant]),
          s"must not produce UnparseableConstant when no validator registered: $errs",
        )
      case Right(_)   => fail("expected Left for sort with no validator")

  test("a BoundVar or satisfying-set variable no query text can produce throws"):
    intercept[IllegalArgumentException](BoundVar("risk-id", asset))
    val f = Formula.Atom(FOL("leaf", List(Term.mkVar("x"))))
    intercept[IllegalArgumentException](QueryBinder.bindSatisfyingFormula(f, "risk-id", catalog))

  // ==================== Typed fields of each bind error ====================

  private def atom(p: String, args: Term*): Formula[FOL] = Formula.Atom(FOL(p, args.toList))

  private def bindErrors(
    range: Formula[FOL],
    scope: Formula[FOL],
    answerVars: List[String] = Nil,
    cat: TypeCatalog = catalog,
  ): List[TypeCheckError] =
    val query = ParsedQuery(Quantifier.mkAtLeast(1, 2), "x", range, scope, answerVars)
    QueryBinder.bind(query, cat) match
      case Left(errs) => errs
      case Right(_)   => fail("expected the binder to refuse the query")

  private val leafX = atom("leaf", Term.mkVar("x"))

  test("UnknownPredicate and UnknownFunction carry the symbol's text"):
    assertEquals(bindErrors(leafX, atom("nope", Term.mkVar("x"))), List(TypeCheckError.UnknownPredicate(SymbolText.of("nope"))))
    assertEquals(
      bindErrors(leafX, atom("gt_loss", Term.mkFn("p99", List(Term.mkVar("x"))), Term.mkConst("5"))),
      List(TypeCheckError.UnknownFunction(SymbolText.of("p99"))),
    )

  test("an arity mismatch carries SymbolKind.Predicate on an atom and SymbolKind.Function on an application"):
    val onAtom = bindErrors(leafX, atom("leaf", Term.mkVar("x"), Term.mkVar("x")))
    assertEquals(onAtom, List(TypeCheckError.ArityMismatch(SymbolText.of("leaf"), SymbolKind.Predicate, 1, 2)))
    assertEquals(onAtom.map(_.message), List("arity mismatch for 'leaf': expected 1, actual 2"))
    val onFn   = bindErrors(
      leafX,
      atom("gt_loss", Term.mkFn("p95", List(Term.mkVar("x"), Term.mkVar("x"))), Term.mkConst("5")),
    )
    assertEquals(onFn, List(TypeCheckError.ArityMismatch(SymbolText.of("p95"), SymbolKind.Function, 1, 2)))
    assertEquals(onFn.map(_.message), List("arity mismatch for 'p95': expected 1, actual 2"))

  test("UnknownConstantOrLiteral carries the constant's text"):
    assertEquals(
      bindErrors(leafX, atom("leaf", Term.mkConst("zz"))),
      List(TypeCheckError.UnknownConstantOrLiteral(LiteralText("zz"))),
    )

  test("TypeMismatch names its site: a variable, a constant, or a function's return"):
    assertEquals(
      bindErrors(leafX, atom("gt_loss", Term.mkVar("x"), Term.mkConst("5"))),
      List(TypeCheckError.TypeMismatch(loss, asset, MismatchSite.Variable(VariableText.of("x")))),
    )
    val withConstant = TypeCatalog.unsafe(
      types = Set(DomainType(asset), ValueType(loss)),
      constants = Map("acme" -> asset),
      predicates = Map(SymbolName("leaf") -> PredicateSig(List(asset)), SymbolName("big") -> PredicateSig(List(loss))),
    )
    assertEquals(
      bindErrors(leafX, atom("big", Term.mkConst("acme")), cat = withConstant),
      List(TypeCheckError.TypeMismatch(loss, asset, MismatchSite.Constant(LiteralText("acme")))),
    )
    assertEquals(
      bindErrors(leafX, atom("gt_prob", Term.mkFn("p95", List(Term.mkVar("x"))), Term.mkConst("0.5"))),
      List(TypeCheckError.TypeMismatch(prob, loss, MismatchSite.FunctionReturn(SymbolText.of("p95")))),
    )

  test("UnboundAnswerVar, UnconstrainedVar and ConflictingTypes carry the variable's text"):
    assertEquals(bindErrors(leafX, leafX, answerVars = List("z")), List(TypeCheckError.UnboundAnswerVar(VariableText.of("z"))))
    assertEquals(
      bindErrors(atom("leaf", Term.mkVar("y")), leafX, answerVars = List("y")),
      List(TypeCheckError.UnconstrainedVar(VariableText.of("x"))),
    )
    val conflict = Formula.And(
      atom("gt_loss", Term.mkVar("y"), Term.mkConst("5")),
      atom("gt_prob", Term.mkVar("y"), Term.mkConst("0.5")),
    )
    assertEquals(
      bindErrors(leafX, conflict),
      List(TypeCheckError.ConflictingTypes(VariableText.of("y"), loss, prob)),
    )

  test("each TypeCheckError's message is its sentence, word for word"):
    import TypeCheckError.*
    val cases: List[(TypeCheckError, String)] = List(
      UnknownPredicate(SymbolText.of("nope"))                                 -> "unknown predicate: nope",
      UnknownFunction(SymbolText.of("+"))                                     -> "unknown function: +",
      ArityMismatch(SymbolText.of("leaf"), SymbolKind.Predicate, 1, 2)        -> "arity mismatch for 'leaf': expected 1, actual 2",
      UnknownConstantOrLiteral(LiteralText("IT Risk"))                        -> "unknown constant or literal: IT Risk",
      TypeMismatch(loss, asset, MismatchSite.Variable(VariableText.of("x")))  -> "type mismatch in variable 'x': expected Loss, actual Asset",
      TypeMismatch(loss, asset, MismatchSite.Constant(LiteralText("acme")))   -> "type mismatch in constant 'acme': expected Loss, actual Asset",
      TypeMismatch(prob, loss, MismatchSite.FunctionReturn(SymbolText.of("p95"))) -> "type mismatch in function 'p95' return: expected Probability, actual Loss",
      UnboundAnswerVar(VariableText.of("z"))                                  -> "unbound answer variable: z",
      UnconstrainedVar(VariableText.of("x"))                                  -> "unconstrained quantifier variable: x",
      ConflictingTypes(VariableText.of("y"), loss, prob)                      -> "conflicting inferred types for 'y': Loss vs Probability",
      UnparseableConstant(LiteralText("abc"), loss, LiteralText("abc"))       -> "cannot parse 'abc' as Loss",
      TypeNotQuantifiable(loss)                                               -> "type 'Loss' is not a domain type and cannot be quantified over",
      UnexpectedFreeVar(VariableText.of("y'"))                                -> "unexpected free variable 'y'': a satisfying-set formula may only mention its one variable",
    )
    cases.foreach((error, sentence) => assertEquals(error.message, sentence))

end QueryBinderSpec
