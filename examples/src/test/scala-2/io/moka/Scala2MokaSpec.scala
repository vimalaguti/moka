package io.moka

import Scala2Definitions._

/** Scala 2-only usage: @moka generates the companion object when none is defined. */
class Scala2MokaSpec extends munit.FunSuite {

  test("@moka generates the companion when missing") {
    assertEquals(NoCompanion.Fields.a, "a")
  }

  test("@moka(name) without companion uses the custom name") {
    assertEquals(RenamedNoCompanion.Renamed.a, "a")
  }
}
