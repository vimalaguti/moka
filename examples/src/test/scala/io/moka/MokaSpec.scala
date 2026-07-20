package io.moka

import Definitions._

class MokaSpec extends munit.FunSuite {

  test("two fields simple debug") {
    assertEquals(ManyFields.Fields.a, "a")
    assertEquals(ManyFields.Fields.b, "b")
  }

  test("no fields") {
    assertNotEquals(NoFields.Fields, null)
  }

  test("one field") {
    assertEquals(OneField.Fields.a, "a")
  }

  test("nested anyval field") {
    assertEquals(NestedSimple.Fields.a, "a")
  }

  test("nested case class field") {
    assertEquals(NestedClass.Fields.a, "a")
  }

  test("companion object with existing members") {
    assertEquals(WithCompanionObject.Fields.a, "a")
    assertEquals(WithCompanionObject.b, 3)
    assertEquals(WithCompanionObject.someFunction(5).a, 5)
  }

  test("bson property rename") {
    assertEquals(DiffFields.Fields.a, "abc")
    assertEquals(DiffFields.Fields.b, "b")
  }

  test("bson property annotated class") {
    assertEquals(BsonAnnotatedClass.Fields.a, "b")
  }

  test("zio bsonField annotated class") {
    assertEquals(ZioBsonAnnotatedClass.Fields.a, "b")
  }

  test("renamed fields object") {
    assertEquals(RenamedObject.Renamed.a, "a")
  }

  test("bson annotated class with companion members") {
    assertEquals(BsonAnnotatedClassCO.Fields.a, "b")
    assertEquals(BsonAnnotatedClassCO.a, 0)
  }

  test("zio bson annotated class with companion members") {
    assertEquals(ZioBsonAnnotatedClassCO.Fields.a, "b")
    assertEquals(ZioBsonAnnotatedClassCO.a, 0)
  }

  test("misspelled field names do not compile") {
    val errors = compileErrors("ManyFields.Fields.typo")
    assert(errors.contains("value typo is not a member"), errors)
  }

  test("field names are literal types") {
    val a: "a" = ManyFields.Fields.a
    assertEquals(a, "a")
  }

  test("bson renamed fields are literal types of the bson name") {
    val abc: "abc" = DiffFields.Fields.a
    assertEquals(abc, "abc")
  }
}
