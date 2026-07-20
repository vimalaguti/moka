package io.moka

import Scala3Definitions._

/** Scala 3-only usage: generateFields without the @moka annotation. */
class Scala3MokaSpec extends munit.FunSuite {

  test("generateFields works without @moka") {
    assertEquals(PlainFields.Fields.a, "a")
    assertEquals(PlainFields.Fields.b, "b")
  }
}
