package io.moka

class Scala3LargeSpec extends munit.FunSuite:

  /** Depth 5, 3 nested case classes and 12 strings per level: about 4,400
    * leaves under one root. Building that tree in code made the root's
    * initializer exceed the JVM's 64 KB method limit ("Method too large"), and
    * its encoding spans more than one string-constant chunk.
    */
  private def model: String =
    val (width, depth, branching) = (12, 5, 3)
    val classes =
      for
        level <- 0 to depth
        idx   <- 0 until math.pow(branching, level).toInt
      yield
        val strings = (0 until width).map(i => s"s$i: String")
        val nested =
          if level == depth then Nil
          else
            (0 until branching).map(b =>
              s"n$b: L${level + 1}_${idx * branching + b}"
            )
        s"final case class L${level}_$idx(${(strings ++ nested).mkString(", ")})"
    s"""package large
       |import io.moka.*
       |${classes.mkString("\n")}
       |object Root:
       |  val Fields = generateFields[L0_0]
       |  val deepest: "n2.n2.n2.n2.n2.s11" = Fields.n2.n2.n2.n2.n2.s11
       |""".stripMargin

  test("a root with thousands of fields compiles down to bytecode") {
    val diags = CompileHarness.compile("Large.scala" -> model)
    assertEquals(diags.filter(_.severity == "ERROR"), Nil)
  }
