package privacy

import io.moka._

class RenamingModePrivacySpec extends munit.FunSuite {

  test("RenamingMode is not accessible outside package io.moka") {
    val err = compileErrors("val x: RenamingMode = ???")
    assert(
      err.toLowerCase.contains("not found") || err.toLowerCase.contains(
        "cannot be accessed"
      ),
      err
    )
  }
}
