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

  test("@moka on a non-case class is a compile error") {
    val errors = compileErrors("""
      object NotACase {
        @moka class Plain(a: Int)
      }
    """)
    assert(errors.contains("not a case class"), errors)
  }

  test("a nested type declared beside the annottee is rejected") {
    val errors = compileErrors("""
      object Local {
        case class Inner(b: Int)
        @moka case class Outer(a: Inner)
        object Outer { val Fields = generateFields[Outer] }
      }
    """)
    assert(errors.contains("cannot resolve type"), errors)
    assert(errors.contains("same enclosing object"), errors)
  }

  test("@moka generates a companion with nested descent") {
    assertEquals(NestedNoCompanion.Fields.inner.deep, "inner.z")
    assertEquals(NestedNoCompanion.Fields.inner._path, "inner")
  }

  test("@moka generates array operators without a companion") {
    assertEquals(NestedNoCompanion.Fields.items._matched.deep, "items.$.z")
    assertEquals(NestedNoCompanion.Fields.items._all.deep, "items.$[].z")
  }

  test("@moka(name) descends into nested types too") {
    assertEquals(NestedRenamed.Paths.inner.deep, "inner.z")
  }

  test("an existing companion is extended with nested descent") {
    assertEquals(NestedWithCompanion.Fields.inner.deep, "inner.z")
    assertEquals(NestedWithCompanion.default.inner.deep, 0)
  }

  // Note: generateFields without @moka is rejected by @compileTimeOnly
  // ("placeholder rewritten by @moka"). Not tested via munit compileErrors:
  // on Scala 2 it typechecks without the refchecks phase that enforces
  // @compileTimeOnly, so the error is only visible in real compilation.
}
