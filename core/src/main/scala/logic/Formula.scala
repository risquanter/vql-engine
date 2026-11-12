package logic

import lexer.VariableText

/**
 * Formulas over atoms of type `A`.
 *
 * Corresponds to OCaml: type ('a) formula = False | True | Atom of 'a | Not of ('a)formula | …
 *
 * `Forall` and `Exists` hold a variable name, so their constructors are closed to this package.
 * Outside it they are built by [[Formula.mkForall]] and [[Formula.mkExists]], which refuse a name
 * no query text can produce.
 */
enum Formula[+A]:
  case False
  case True
  case Atom(value: A)
  case Not(p: Formula[A])
  case And(p: Formula[A], q: Formula[A])
  case Or(p: Formula[A], q: Formula[A])
  case Imp(p: Formula[A], q: Formula[A])
  case Iff(p: Formula[A], q: Formula[A])
  case Forall private[logic] (x: String, p: Formula[A])
  case Exists private[logic] (x: String, p: Formula[A])

object Formula:
  /** Building functions, as OCaml's `mk_and`, `mk_forall` and the rest. */
  def mkAnd[A](p: Formula[A], q: Formula[A]): Formula[A] = And(p, q)

  def mkOr[A](p: Formula[A], q: Formula[A]): Formula[A] = Or(p, q)

  def mkImp[A](p: Formula[A], q: Formula[A]): Formula[A] = Imp(p, q)

  def mkIff[A](p: Formula[A], q: Formula[A]): Formula[A] = Iff(p, q)

  /** Throws `IllegalArgumentException` for a variable name no query text can produce. */
  def mkForall[A](x: String, p: Formula[A]): Formula[A] =
    require(VariableText.admits(x), "A variable name must be a word or a decimal")
    Forall(x, p)

  /** Throws `IllegalArgumentException` for a variable name no query text can produce. */
  def mkExists[A](x: String, p: Formula[A]): Formula[A] =
    require(VariableText.admits(x), "A variable name must be a word or a decimal")
    Exists(x, p)

  /** Convenience constructors for common cases to stay consistent */
  def atom[A](a: A): Formula[A]         = Atom(a)
  def not[A](p: Formula[A]): Formula[A] = Not(p)

  /** Example: true */
  def trueFormula[A]: Formula[A] = True

  /** Example: false */
  def falseFormula[A]: Formula[A] = False

end Formula
