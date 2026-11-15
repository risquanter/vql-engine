package vql.typed

import munit.FunSuite

class OrderedDomainSpec extends FunSuite:

  test("of keeps each distinct carrier once, in the ordering's order") {
    assertEquals(OrderedDomain.of(List("b", "a", "c", "a")).carriers, Vector("a", "b", "c"))
  }

  test("of refuses an ordering that ranks two distinct carriers equal") {
    intercept[IllegalArgumentException] {
      OrderedDomain.of(List("ab", "cd"))(using Ordering.by[String, Int](_.length))
    }
  }

  test("a model's elements are Values of the domain's sort, in the domain's order") {
    val item       = TypeId("Item")
    val dispatcher = MapDispatcher(predicates = Map.empty, functions = Map.empty)
    val model      = RuntimeModel(domains = Map(item -> OrderedDomain.of(List(3L, 1L, 2L))), dispatcher = dispatcher)
    assertEquals(model.elements(item), Vector(Value(item, 1L), Value(item, 2L), Value(item, 3L)))
  }

end OrderedDomainSpec
