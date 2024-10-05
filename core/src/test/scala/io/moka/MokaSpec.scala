package io.moka

import Moka.moka
import io.moka.TestDefinitions._

class MokaSpec extends munit.FunSuite {

  test("no fields") {
    NoFields.Fields
  }

  test("one field") {
    assertEquals(OneField.Fields.a, "a")
  }

  test("two fields") {
    assertEquals(ManyFields.Fields.a, "a")
    assertEquals(ManyFields.Fields.b, "b")
  }

  test("two fields primitive types") {
    assertEquals(DiffFields.Fields.a, "a")
    assertEquals(DiffFields.Fields.b, "b")
  }

  test("non primitive type") {
    assertEquals(NestedSimple.Fields.a, "a")
  }

  test("supports defined companion object".ignore) {
    assertEquals(WithCompanionObject.b, 3)
    // assertEquals(WithCompanionObject.Fields.a, "a")
  }

  test("supports nested case classes".ignore) {
    // assertEquals(NestedClass.Fields.one.name, "one")
    // assertEquals(NestedClass.Fields.one.a, "a")
  }

}

object TestDefinitions {

  @moka
  final case class NoFields()
  @moka
  final case class OneField(a: Int)
  @moka
  final case class ManyFields(a: Int, b: Int)
  @moka
  final case class DiffFields(a: Int, b: String)
  case class A(value: Int) extends AnyVal
  @moka
  case class NestedSimple(a: A)
  // @moka
  case class WithCompanionObject(a: Int)
  object WithCompanionObject {
    val b = 3
  }
  @moka
  final case class NestedClass(one: OneField)

}
