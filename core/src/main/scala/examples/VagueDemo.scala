package examples

import parser.FOLParser
import vql.parser.VagueQueryParser
import vql.semantics.VagueSemantics
import vql.sampling.SamplingParams
import vql.typed.{FolModel, RuntimeModel, RuntimeDispatcher, TypeCatalog, PredicateSig, TypeId, SymbolName, Value}
import vql.typed.TypeDecl.DomainType

/** Runnable demonstration of the typed vague-quantifier pipeline.
  *
  * Exercises the full public API in one pass: declare a [[TypeCatalog]], provide
  * a [[RuntimeModel]] (domain plus dispatcher), combine them into a validated
  * [[FolModel]], then run a plain query, a compound-range query with negation,
  * and a bare-formula `satisfyingSet`. Every step returns `Either[QueryError, _]`.
  */
@main def VagueDemo(): Unit =
  val asset = TypeId("Asset")

  // Predicate extensions over the Asset domain a1..a6.
  val extensions: Map[String, Set[String]] = Map(
    "monitored"   -> Set("a1", "a2", "a3", "a4", "a5"),
    "critical"    -> Set("a1", "a2", "a3", "a4"),
    "server"      -> Set("a1", "a2", "a3", "a4"),
    "patched"     -> Set("a1", "a2"),
    "exploitable" -> Set("a3", "a5")
  )

  // 1. Declare sorts and predicate signatures.
  val catalog = TypeCatalog.unsafe(
    types = Set(DomainType(asset)),
    predicates = extensions.keys.map(name => SymbolName(name) -> PredicateSig(List(asset))).toMap
  )

  // 2. Provide the runtime data: the domain of each sort plus a dispatcher.
  val assets = (1 to 6).map(i => Value(asset, s"a$i")).toSet
  val dispatcher = new RuntimeDispatcher:
    def evalPredicate(name: SymbolName, args: List[Value]): Either[String, Boolean] =
      extensions.get(name.value) match
        case Some(ext) => Right(args.headOption.exists(v => ext.contains(v.raw.toString)))
        case None      => Left(s"no predicate: ${name.value}")
    def evalFunction(name: SymbolName, args: List[Value]): Either[String, Any] =
      Left(s"no function: ${name.value}")
    def predicateSymbols: Set[SymbolName] = extensions.keys.map(SymbolName(_)).toSet
    def functionSymbols: Set[SymbolName]  = Set.empty

  val runtimeModel = RuntimeModel(domains = Map(asset -> assets), dispatcher = dispatcher)

  // 3. Build the validated model once; reuse it for every query.
  val folModel = FolModel(catalog, runtimeModel) match
    case Right(m)  => m
    case Left(err) => sys.error(s"model construction failed: ${err.formatted}")

  println("=" * 72)
  println("Vague-quantifier typed pipeline — demonstration")
  println("=" * 72)

  // Example 1: plain range. "At least 3/4 of monitored assets are critical."
  runQuery(
    folModel,
    "At least 3/4 of monitored assets are critical",
    """Q[>=]^{3/4} x (monitored(x), critical(x))"""
  )

  // Example 2: compound range with closed-world negation.
  // "About half of unpatched servers are exploitable."
  runQuery(
    folModel,
    "About half of unpatched servers are exploitable",
    """Q[~]^{1/2} x (server(x) /\ ~patched(x), exploitable(x))"""
  )

  // Example 3: satisfyingSet — the exact set of a bare formula's one free variable.
  println()
  println("satisfyingSet: assets satisfying critical(x)")
  val formula = FOLParser.parse("critical(x)")
    .fold(e => sys.error(s"parse failed: ${e.message}"), identity)
  VagueSemantics.satisfyingSet(formula, "x", folModel) match
    case Right(set) => println(s"  => ${set.map(_.raw).toList.sortBy(_.toString).mkString("{", ", ", "}")}")
    case Left(err)  => println(s"  error: ${err.formatted}")

  println()
  println("=" * 72)

/** Parse, evaluate, and print one vague query against a prepared model. */
private def runQuery(folModel: FolModel, label: String, queryStr: String): Unit =
  println()
  println(label)
  println(s"  query: $queryStr")
  val outcome =
    for
      query  <- VagueQueryParser.parse(queryStr)
      output <- VagueSemantics.evaluateTyped(query, folModel, samplingParams = SamplingParams.exact)
    yield output
  outcome match
    case Right(output) =>
      val pct = f"${output.proportion * 100}%.0f%%"
      println(s"  satisfied:   ${output.satisfied}")
      println(s"  proportion:  $pct (${output.result.satisfyingCount}/${output.rangeElements.size})")
      println(s"  range:       ${output.rangeElements.map(_.raw).toList.sortBy(_.toString).mkString("{", ", ", "}")}")
      println(s"  satisfying:  ${output.satisfyingElements.map(_.raw).toList.sortBy(_.toString).mkString("{", ", ", "}")}")
    case Left(err) =>
      println(s"  error: ${err.formatted}")
