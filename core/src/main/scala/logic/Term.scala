package logic

import lexer.{LiteralText, SymbolText, VariableText}

/**
 * Terms in first-order logic
 *
 * Corresponds to OCaml: type term = Var of string | Fn of string * term list
 *
 * The case constructors are closed to this package. Outside it a term is built by [[Term.mkVar]],
 * [[Term.mkFn]] or [[Term.mkConst]], each of which refuses a name no query text can produce, and
 * is read by pattern matching on the cases as usual.
 */
enum Term:
  /** Variable: x, y, foo, etc. */
  case Var private[logic] (name: String)

  /**
   * Function application: f(t1, ..., tn)
   */
  case Fn private[logic] (name: String, args: List[Term])

  /** Type-safe representation for constants */
  case Const private[logic] (name: String)

object Term:

  /** A variable. Throws `IllegalArgumentException` for a name no query text can produce. */
  def mkVar(name: String): Term =
    require(VariableText.admits(name), "A variable name must be a word or a decimal")
    Var(name)

  /** A function application. Throws `IllegalArgumentException` for a name no query text can produce. */
  def mkFn(name: String, args: List[Term]): Term =
    require(SymbolText.admitsFunction(name), "A function name must be a word, a decimal or a term operator")
    Fn(name, args)

  /** A constant. Throws `IllegalArgumentException` for text outside the literal alphabet. */
  def mkConst(name: String): Term =
    require(LiteralText.admits(name), "A constant must stay within the literal alphabet")
    Const(name)

  /**
   * Example from fol.ml: sqrt(1 - cos(power(x + y, 2)))
   *
   * Uses [[Term.Const]] for the numeric literals 1 and 2 — the idiomatic representation since
   * `Term.Const` was added (deviation from Harrison OCaml) to distinguish inline literals from
   * zero-arity function applications.
   */
  def example: Term =
    Fn(
      "sqrt",
      List(
        Fn(
          "-",
          List(
            Const("1"),
            Fn(
              "cos",
              List(
                Fn(
                  "power",
                  List(
                    Fn("+", List(Var("x"), Var("y"))),
                    Const("2"),
                  ),
                )
              ),
            ),
          ),
        )
      ),
    )

end Term
