package parser

import lexer.Lexer
import munit.FunSuite
import parser.NestingFailure.{ParenthesesTooDeep, TooManyOperators}

class NestingBoundSpec extends FunSuite:

  private def checked(s: String): Either[NestingFailure, Unit] =
    NestingBound.check(Lexer.lexString(s))

  private def nested(depth: Int): String = "(" * depth + "p(x)" + ")" * depth

  private def chain(atoms: Int): String = List.fill(atoms)("p").mkString(" \\/ ")

  private def quantified(variables: Int): String =
    "forall " + (1 to variables).map(i => s"x$i").mkString(" ") + " . p(x1)"

  test("parentheses at the depth limit pass, and one more is refused") {
    // p(x) holds one parenthesis of its own
    assertEquals(checked(nested(NestingBound.MaxParenthesisDepth - 1)), Right(()))
    assertEquals(checked(nested(NestingBound.MaxParenthesisDepth)), Left(ParenthesesTooDeep(64)))
  }

  test("operators at the limit pass, and one more is refused") {
    assertEquals(checked(chain(NestingBound.MaxOperators + 1)), Right(()))
    assertEquals(checked(chain(NestingBound.MaxOperators + 2)), Left(TooManyOperators(256)))
  }

  test("each quantified variable counts as an operator") {
    assertEquals(checked(quantified(NestingBound.MaxOperators)), Right(()))
    assertEquals(checked(quantified(NestingBound.MaxOperators + 1)), Left(TooManyOperators(256)))
  }

  test("the words after a quantifier's dot are not counted") {
    // x and /\ count 2; p, y, q and z count nothing
    val base = "forall x . p(y) /\\ q(z)"
    assertEquals(checked(base + " /\\ r" * (NestingBound.MaxOperators - 2)), Right(()))
    assertEquals(checked(base + " /\\ r" * (NestingBound.MaxOperators - 1)), Left(TooManyOperators(256)))
  }

  test("an unmatched closing parenthesis is left to the parser") {
    assertEquals(checked("p(x))"), Right(()))
  }

  test("each failure's message names its limit") {
    assertEquals(ParenthesesTooDeep(64).message, "Parentheses nest deeper than 64")
    assertEquals(
      TooManyOperators(256).message,
      "The formula holds more than 256 operators and quantified variables",
    )
  }

end NestingBoundSpec
