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

      @scala.annotation.nowarn("msg=moka")
      final case class Silenced(@jsonField("j") a: String)
      object Silenced:
        val Fields = generateFields[Silenced]

      final case class SilencedField(
        @scala.annotation.nowarn("msg=moka") @jsonField("j") a: String
      )
      object SilencedField:
        val Fields = generateFields[SilencedField]

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

  test("the warning points at the first field renamed by the other format") {
    val diags = CompileHarness.compile("WarnPos.scala" -> """
      package warnpos
      import io.moka.*
      import zio.json.jsonField
      final case class O(
        x: Int,
        @jsonField("j") a: String,
        @jsonField("k") b: String
      )
      object O:
        val Fields = generateFields[O]
    """)
    assertEquals(
      diags.filter(_.msg.startsWith("moka:")).map(d => (d.severity, d.line)),
      List(("WARNING", 7)),
      diags
    )
  }

  test("a renaming conflict points at the field, in its own file") {
    val diags = CompileHarness.compile(
      "Inner.scala" -> """
      package conflictpos
      final case class Inner(
        @org.mongodb.scala.bson.annotations.BsonProperty("b1")
        @zio.bson.bsonField("b2")
        name: String
      )
      """,
      "Outer.scala" -> """
      package conflictpos
      import io.moka.*
      final case class Outer(
        x: Int,
        @org.mongodb.scala.bson.annotations.BsonProperty("c1")
        @zio.bson.bsonField("c2")
        own: String
      )
      object Outer:
        val Fields = generateFields[Outer]
      final case class Wrapper(inner: Inner)
      object Wrapper:
        val Fields = generateFields[Wrapper]
      """
    )
    val moka = diags.filter(_.msg.startsWith("moka: conflicting BSON"))
    assertEquals(
      moka.map(d => (d.severity, d.line)).sorted,
      List(("ERROR", 6), ("ERROR", 8)),
      diags
    )
  }
