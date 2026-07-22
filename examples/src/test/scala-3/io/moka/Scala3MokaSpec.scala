package io.moka

import Scala3Definitions.*

/** Scala 3-only usage: generateFields without the @moka annotation. */
class Scala3MokaSpec extends munit.FunSuite {

  test("generateFields works without @moka") {
    assertEquals(PlainFields.Fields.a, "a")
    assertEquals(PlainFields.Fields.b, "b")
  }

  test("bson annotations rename the field value") {
    assertEquals(BsonFields.Fields.a, "renamed")
    assertEquals(BsonFields.Fields.b, "znamed")
  }

  test("custom name is just the val name") {
    assertEquals(CustomName.Renamed.a, "a")
  }

  test("existing companion members are preserved") {
    assertEquals(WithMembers.Fields.a, "a")
    assertEquals(WithMembers.default, WithMembers(0))
  }

  test("nested case class uses the outer field name") {
    assertEquals(Nested.Fields.inner, "inner")
  }

  test("generateFields on a non-case class is a compile error") {
    val errors = compileErrors("generateFields[String]")
    assert(errors.contains("requires a case class"), errors)
  }
}
