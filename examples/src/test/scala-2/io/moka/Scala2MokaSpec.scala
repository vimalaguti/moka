package io.moka

import Scala2Definitions._

/** Scala 2-only usage: @moka generates or rewrites the companion object,
  * no placeholder val needed.
  */
class Scala2MokaSpec extends munit.FunSuite {

  test("@moka generates the companion when missing") {
    assertEquals(NoCompanion.Fields.a, "a")
  }

  test("@moka(name) without companion uses the custom name") {
    assertEquals(RenamedNoCompanion.Renamed.a, "a")
  }

  test("many fields without companion") {
    assertEquals(ManyFieldsNoCompanion.Fields.a, "a")
    assertEquals(ManyFieldsNoCompanion.Fields.b, "b")
  }

  test("bson property rename without companion") {
    assertEquals(BsonNoCompanion.Fields.a, "renamed")
  }

  test("zio bsonField rename without companion") {
    assertEquals(ZioBsonNoCompanion.Fields.a, "renamed")
  }

  test("existing companion members are preserved") {
    assertEquals(WithMembers.Fields.a, "a")
    assertEquals(WithMembers.default, WithMembers(0))
  }

  test("@moka(name) with existing companion uses the custom name") {
    assertEquals(RenamedWithCompanion.Renamed.a, "a")
    assertEquals(RenamedWithCompanion.default.a, 1)
  }

  // Note: generateFields without @moka is rejected by @compileTimeOnly
  // ("placeholder rewritten by @moka"). Not tested via munit compileErrors:
  // on Scala 2 it typechecks without the refchecks phase that enforces
  // @compileTimeOnly, so the error is only visible in real compilation.
}
