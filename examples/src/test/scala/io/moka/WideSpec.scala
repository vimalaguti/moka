package io.moka

class WideSpec extends munit.FunSuite {

  test("a case class with more than 22 fields") {
    val first: "renamed_f1" = Wide30.Fields.f1
    val last: "f30"         = Wide30.Fields.f30
    assertEquals(first, "renamed_f1")
    assertEquals(last, "f30")
  }

  test("a wide case class nested in a wide one") {
    val o24: "o24"                = WideOuter.Fields.o24
    val nested: "wide.renamed_f1" = WideOuter.Fields.wide.f1
    val deep: "wide.f30"          = WideOuter.Fields.wide.f30
    assertEquals(o24, "o24")
    assertEquals(nested, "wide.renamed_f1")
    assertEquals(deep, "wide.f30")
    assertEquals(WideOuter.Fields.wide._path, "wide")
  }
}
