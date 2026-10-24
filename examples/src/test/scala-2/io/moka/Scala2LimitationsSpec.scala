package io.moka

/** Known 2.13 limitations (KNOWN_LIMITATIONS.md), each written as the behaviour
  * we would want and ignored until it holds.
  */
class Scala2LimitationsSpec extends munit.FunSuite {

  // An annotation that never expands cannot report anything: the companion it
  // would have created is simply missing.
  test("L-noflag: @moka without -Ymacro-annotations names the flag".ignore) {
    val diags = CompileHarness.compileWith("-Xsource:3")("NoFlag.scala" -> """
      package noflag
      import io.moka._

      @moka final case class Fruit(name: String)
      object Use { val n = Fruit.Fields.name }
    """)
    val errors = diags.filter(_.severity == "ERROR").map(_.msg)
    assert(errors.nonEmpty, diags)
    assert(errors.forall(_.contains("-Ymacro-annotations")), errors)
  }

  // Resolving B re-enters A's own expansion: StackOverflowError in the compiler.
  test("L-mutual: a mutually recursive pair expands".ignore) {
    val diags = CompileHarness.compile("Mutual.scala" -> """
      package mutual
      import io.moka._

      @moka final case class A(b: Option[B])
      @moka final case class B(a: Option[A])
    """)
    assertEquals(diags.filter(_.severity == "ERROR"), Nil)
  }
}
