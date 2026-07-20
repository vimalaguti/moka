package io.moka

import Definitions._

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
    assertEquals(DiffFields.Fields.a, "abc")
    assertEquals(DiffFields.Fields.b, "b")
  }

  test("non primitive type") {
    assertEquals(NestedSimple.Fields.a, "a")
  }

  test("supports defined companion object") {
    assertEquals(WithCompanionObject.b, 3)
    assertEquals(WithCompanionObject.someFunction(3), WithCompanionObject(3))
    assertEquals(WithCompanionObject.Fields.a, "a")
  }

  test("supports defining a different name") {
    assertEquals(RenamedObject.Renamed.a, "a")
  }

  test("supports bson annotations") {
    assertEquals(BsonAnnotatedClass(1).a, 1)
    assertEquals(BsonAnnotatedClass.Fields.a, "b")
  }

  test("supports zio bson annotations") {
    assertEquals(ZioBsonAnnotatedClass(1).a, 1)
    assertEquals(ZioBsonAnnotatedClass.Fields.a, "b")
  }

  test("annotations works with companion object") {
    assertEquals(BsonAnnotatedClassCO.Fields.a, "b")
    assertEquals(BsonAnnotatedClassCO.a, 0)
    assertEquals(ZioBsonAnnotatedClassCO.Fields.a, "b")
    assertEquals(ZioBsonAnnotatedClassCO.a, 0)
  }
}
