package io.moka

class Scala3WarningSpec extends munit.FunSuite:

  test(
    "an object ignoring the only renames there are warns; @nowarn silences it"
  ) {
    val diags = CompileHarness.compile("Warn.scala" -> """
      package warn
      import io.moka.*
      import zio.json.jsonField

      final case class JsonOnly(@jsonField("j") a: String)
      object JsonOnly:
        val Fields = generateFields[JsonOnly]

      final case class Silenced(@jsonField("j") a: String)
      object Silenced:
        @scala.annotation.nowarn("msg=moka")
        val Fields = generateFields[Silenced]

      final case class Json(@jsonField("j") a: String)
      object Json:
        val JsonFields = generateJsonFields[Json]
    """)
    val warnings = diags.filter(_.severity == "WARNING").map(_.msg)
    assert(diags.forall(_.severity != "ERROR"), diags)
    assertEquals(
      warnings.count(_.contains("renamed only by JSON")),
      1,
      warnings
    )
    assert(warnings.exists(_.contains("JsonOnly's fields")), warnings)
    assert(warnings.exists(_.contains("generateJsonFields")), warnings)
  }
