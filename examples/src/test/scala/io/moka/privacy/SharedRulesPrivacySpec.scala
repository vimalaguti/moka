package privacy

class SharedRulesPrivacySpec extends munit.FunSuite {

  private def assertInaccessible(err: String): Unit =
    assert(
      err.toLowerCase.contains("not found") || err.toLowerCase.contains(
        "cannot be accessed"
      ),
      err
    )

  test("MokaFormat is not accessible outside package io.moka") {
    assertInaccessible(
      compileErrors("val x: io.moka.MokaFormat = io.moka.MokaFormat.Bson")
    )
  }

  test("MokaRules is not accessible outside package io.moka") {
    assertInaccessible(
      compileErrors("val x = io.moka.MokaRules.arrayOperators")
    )
  }
}
