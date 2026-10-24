package io.moka

/** Known Scala 3 limitations (KNOWN_LIMITATIONS.md), each written as the
  * behaviour we would want and ignored until it holds.
  */
class Scala3LimitationsSpec extends munit.FunSuite:

  private val model = """
    package model
    import io.moka.*

    final case class Leaf(a: String, b: String, c: String)
    final case class Mid(x: Leaf, y: Leaf, z: Leaf)
    final case class Root(p: Mid, q: Mid)
    object Root:
      val Fields = generateFields[Root]
  """

  // The error prints the whole refined type of the generated value.
  test("L-typo: a typo's error stays short".ignore) {
    val diags = CompileHarness.compile(
      "Model.scala" -> model,
      "Use.scala"   -> "package model\nval t = Root.Fields.p.x.typo\n"
    )
    val errors = diags.filter(_.severity == "ERROR")
    assertEquals(errors.size, 1, diags)
    assert(errors.head.msg.linesIterator.size <= 5, errors.head.msg)
  }
