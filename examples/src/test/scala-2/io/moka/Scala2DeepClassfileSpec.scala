package io.moka

/** Nested types from a classfile two levels down: the shape check must load the
  * deeper symbol before asking whether it is a case class.
  */
@moka
final case class S2DeepOwner(mid: S2DeepMid)
object S2DeepOwner {
  val Fields = generateFields[S2DeepOwner]
}

class Scala2DeepClassfileSpec extends munit.FunSuite {
  test("a classfile type two levels deep stays a node") {
    assertEquals(S2DeepOwner.Fields.mid.leaf._path, "mid.leaf")
    assertEquals(S2DeepOwner.Fields.mid.leaf.x, "mid.leaf.x")
  }
}
