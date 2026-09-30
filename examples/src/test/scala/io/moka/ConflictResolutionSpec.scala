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

class ConflictResolutionSpec extends munit.FunSuite {

  test(
    "cross-compiled companion with generateBsonFields and generateJsonFields projects both"
  ) {
    assertEquals(SharedDual.BsonFields.variety, "b_var")
    assertEquals(SharedDual.JsonFields.variety, "j_var")
  }

  test("agreeing annotations on generateFields succeed") {
    assertEquals(SharedAgreeing.Fields.field, "common")
  }

  test("conflicting annotations on generateFields fail compilation") {
    val errors = compileErrors("""
      object Conflicting {
        @moka case class Bad(
          @org.mongodb.scala.bson.annotations.BsonProperty("b")
          @zio.json.jsonField("j")
          name: String
        )
        object Bad {
          val Fields = generateFields[Bad]
        }
      }
    """)
    assert(errors.contains("conflicting"), errors)
  }
}
