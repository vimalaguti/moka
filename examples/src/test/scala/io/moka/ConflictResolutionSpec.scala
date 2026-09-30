package io.moka

import org.mongodb.scala.bson.annotations.BsonProperty
import zio.json._

@moka
final case class SharedDual(
    @BsonProperty("b_var") @jsonField("j_var") variety: String
)
object SharedDual {
  val BsonFields = generateBsonFields[SharedDual]
  val JsonFields = generateJsonFields[SharedDual]
}

@moka
final case class SharedAgreeing(
    @BsonProperty("common") @jsonField("common") field: String
)
object SharedAgreeing {
  val Fields = generateFields[SharedAgreeing]
}

@moka
final case class JsonOnlyFruit(
    @jsonField("fruit_kind") kind: String
)
object JsonOnlyFruit {
  val Fields     = generateFields[JsonOnlyFruit]
  val JsonFields = generateJsonFields[JsonOnlyFruit]
}

class ConflictResolutionSpec extends munit.FunSuite {

  test(
    "generateFields ignores @jsonField and preserves original Scala field name"
  ) {
    assertEquals(JsonOnlyFruit.Fields.kind, "kind")
    assertEquals(JsonOnlyFruit.JsonFields.kind, "fruit_kind")
  }

  test(
    "cross-compiled companion with generateBsonFields and generateJsonFields projects both"
  ) {
    assertEquals(SharedDual.BsonFields.variety, "b_var")
    assertEquals(SharedDual.JsonFields.variety, "j_var")
  }

  test("cross-compiled nested BSON projection from Definitions") {
    assertEquals(SharedOuterDual.BsonFields.inner.code, "inner.b_code")
  }

  test("agreeing annotations on generateFields succeed") {
    assertEquals(SharedAgreeing.Fields.field, "common")
  }

  test("conflicting BSON annotations on generateFields fail compilation") {
    val errors = compileErrors("""
      object ConflictingBson {
        @moka case class Bad(
          @org.mongodb.scala.bson.annotations.BsonProperty("b1")
          @zio.bson.bsonField("b2")
          name: String
        )
        object Bad {
          val Fields = generateFields[Bad]
        }
      }
    """)
    assert(errors.contains("conflicting BSON renaming annotations"), errors)
  }

  test("conflicting JSON annotations on generateJsonFields fail compilation") {
    val errors = compileErrors("""
      object ConflictingJson {
        class JsonProperty(name: String) extends scala.annotation.StaticAnnotation
        @mokaJson case class Bad(
          @zio.json.jsonField("j1")
          @JsonProperty("j2")
          name: String
        )
        object Bad {
          val JsonFields = generateJsonFields[Bad]
        }
      }
    """)
    assert(errors.contains("conflicting JSON renaming annotations"), errors)
  }
}
