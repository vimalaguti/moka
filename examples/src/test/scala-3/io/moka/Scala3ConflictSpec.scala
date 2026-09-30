package io.moka

import org.mongodb.scala.bson.annotations.BsonProperty
import zio.json.*

final case class S3BsonOnly(
    @BsonProperty("b") @jsonField("j") name: String
)
object S3BsonOnly {
  val BsonFields = generateBsonFields[S3BsonOnly]
}

final case class S3ZioJsonOnly(
    @BsonProperty("b") @jsonField("j") name: String
)
object S3ZioJsonOnly {
  val ZioJsonFields = generateZioJsonFields[S3ZioJsonOnly]
}

final case class S3Dual(
    @BsonProperty("b_var") @jsonField("j_var") variety: String
)
object S3Dual {
  val BsonFields    = generateBsonFields[S3Dual]
  val ZioJsonFields = generateZioJsonFields[S3Dual]
}

final case class S3Agreeing(
    @BsonProperty("common") @jsonField("common") field: String
)
object S3Agreeing {
  val Fields = generateFields[S3Agreeing]
}

class Scala3ConflictSpec extends munit.FunSuite {

  test("generateBsonFields selects BSON annotations") {
    assertEquals(S3BsonOnly.BsonFields.name, "b")
  }

  test("generateZioJsonFields selects ZIO JSON annotations") {
    assertEquals(S3ZioJsonOnly.ZioJsonFields.name, "j")
  }

  test(
    "companion with generateBsonFields and generateZioJsonFields projects both"
  ) {
    assertEquals(S3Dual.BsonFields.variety, "b_var")
    assertEquals(S3Dual.ZioJsonFields.variety, "j_var")
  }

  test("agreeing annotations on generateFields succeed") {
    assertEquals(S3Agreeing.Fields.field, "common")
  }

  test("conflicting annotations on generateFields fail compilation") {
    val errors = compileErrors("""
      case class Bad(
        @org.mongodb.scala.bson.annotations.BsonProperty("b")
        @zio.json.jsonField("j")
        name: String
      )
      object Bad {
        val Fields = generateFields[Bad]
      }
    """)
    assert(errors.contains("conflicting"), errors)
  }
}
