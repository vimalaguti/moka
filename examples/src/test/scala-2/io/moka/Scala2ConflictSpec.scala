package io.moka

import org.mongodb.scala.bson.annotations.BsonProperty
import zio.json._

@mokaBson
final case class S2BsonOnly(
    @BsonProperty("b") @jsonField("j") name: String
)

@mokaZioJson
final case class S2ZioJsonOnly(
    @BsonProperty("b") @jsonField("j") name: String
)

@moka
final case class S2Dual(
    @BsonProperty("b_var") @jsonField("j_var") variety: String
)
object S2Dual {
  val BsonFields    = generateBsonFields[S2Dual]
  val ZioJsonFields = generateZioJsonFields[S2Dual]
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

  test(
    "@mokaZioJson selects ZIO JSON annotations and defaults to ZioJsonFields"
  ) {
    assertEquals(S2ZioJsonOnly.ZioJsonFields.name, "j")
  }

  test(
    "companion with generateBsonFields and generateZioJsonFields projects both"
  ) {
    assertEquals(S2Dual.BsonFields.variety, "b_var")
    assertEquals(S2Dual.ZioJsonFields.variety, "j_var")
  }

  test("agreeing annotations on generateFields succeed") {
    assertEquals(S2Agreeing.Fields.field, "common")
  }

  test("conflicting annotations on @moka / generateFields fail compilation") {
    val errors = compileErrors("""
      object Conflicting {
        @moka case class Bad(
          @org.mongodb.scala.bson.annotations.BsonProperty("b")
          @zio.json.jsonField("j")
          name: String
        )
      }
    """)
    assert(errors.contains("conflicting"), errors)
  }
}
