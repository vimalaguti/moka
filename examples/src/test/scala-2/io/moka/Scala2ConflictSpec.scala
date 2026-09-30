package io.moka

import org.mongodb.scala.bson.annotations.BsonProperty
import zio.json._

@mokaBson
final case class S2BsonOnly(
    @BsonProperty("b") @jsonField("j") name: String
)

@mokaJson
final case class S2JsonOnly(
    @BsonProperty("b") @jsonField("j") name: String
)

@moka
final case class S2Dual(
    @BsonProperty("b_var") @jsonField("j_var") variety: String
)
object S2Dual {
  val BsonFields = generateBsonFields[S2Dual]
  val JsonFields = generateJsonFields[S2Dual]
}

@moka
final case class S2Agreeing(
    @BsonProperty("common") @jsonField("common") field: String
)
object S2Agreeing {
  val Fields = generateFields[S2Agreeing]
}

class Scala2ConflictSpec extends munit.FunSuite {

  test("@mokaBson selects BSON annotations and defaults to BsonFields") {
    assertEquals(S2BsonOnly.BsonFields.name, "b")
  }

  test("@mokaJson selects JSON annotations and defaults to JsonFields") {
    assertEquals(S2JsonOnly.JsonFields.name, "j")
  }

  test(
    "companion with generateBsonFields and generateJsonFields projects both"
  ) {
    assertEquals(S2Dual.BsonFields.variety, "b_var")
    assertEquals(S2Dual.JsonFields.variety, "j_var")
  }

  test("agreeing annotations on generateFields succeed") {
    assertEquals(S2Agreeing.Fields.field, "common")
  }

  test("conflicting BSON annotations fail compilation") {
    val errors = compileErrors("""
      object Conflicting {
        @moka case class Bad(
          @org.mongodb.scala.bson.annotations.BsonProperty("b1")
          @zio.bson.bsonField("b2")
          name: String
        )
      }
    """)
    assert(errors.contains("conflicting BSON renaming annotations"), errors)
  }

  test("conflicting JSON annotations fail compilation") {
    val errors = compileErrors("""
      object ConflictingJson {
        class JsonProperty(name: String) extends scala.annotation.StaticAnnotation
        @mokaJson case class Bad(
          @zio.json.jsonField("j1")
          @JsonProperty("j2")
          name: String
        )
      }
    """)
    assert(errors.contains("conflicting JSON renaming annotations"), errors)
  }
}
