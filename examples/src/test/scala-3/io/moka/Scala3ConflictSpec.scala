package io.moka

import org.mongodb.scala.bson.annotations.BsonProperty
import zio.json.*

final case class S3BsonOnly(
    @BsonProperty("b") @jsonField("j") name: String
)
object S3BsonOnly {
  val BsonFields = generateBsonFields[S3BsonOnly]
}

final case class S3JsonOnly(
    @BsonProperty("b") @jsonField("j") name: String
)
object S3JsonOnly {
  val JsonFields = generateJsonFields[S3JsonOnly]
}

final case class S3Dual(
    @BsonProperty("b_var") @jsonField("j_var") variety: String
)
object S3Dual {
  val BsonFields = generateBsonFields[S3Dual]
  val JsonFields = generateJsonFields[S3Dual]
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

  test("generateJsonFields selects JSON annotations") {
    assertEquals(S3JsonOnly.JsonFields.name, "j")
  }

  test(
    "companion with generateBsonFields and generateJsonFields projects both"
  ) {
    assertEquals(S3Dual.BsonFields.variety, "b_var")
    assertEquals(S3Dual.JsonFields.variety, "j_var")
  }

  test("agreeing annotations on generateFields succeed") {
    assertEquals(S3Agreeing.Fields.field, "common")
  }

  test("conflicting BSON annotations fail compilation") {
    val errors = compileErrors("""
      case class Bad(
        @org.mongodb.scala.bson.annotations.BsonProperty("b1")
        @zio.bson.bsonField("b2")
        name: String
      )
      object Bad {
        val Fields = generateFields[Bad]
      }
    """)
    assert(errors.contains("conflicting BSON renaming annotations"), errors)
  }

  test("conflicting JSON annotations fail compilation") {
    val errors = compileErrors("""
      class JsonProperty(name: String) extends scala.annotation.StaticAnnotation
      case class BadJson(
        @zio.json.jsonField("j1")
        @JsonProperty("j2")
        name: String
      )
      object BadJson {
        val JsonFields = generateJsonFields[BadJson]
      }
    """)
    assert(errors.contains("conflicting JSON renaming annotations"), errors)
  }

  test("generateBsonFields and generateJsonFields report their own method name on non-case classes") {
    val bsonErr = compileErrors("generateBsonFields[String]")
    assert(bsonErr.contains("generateBsonFields[String] requires a case class"), bsonErr)
    val jsonErr = compileErrors("generateJsonFields[String]")
    assert(jsonErr.contains("generateJsonFields[String] requires a case class"), jsonErr)
  }

  test("chosen format inside nested types (Scala 3)") {
    case class LocalInner(@BsonProperty("b_val") @jsonField("j_val") v: String)
    case class LocalOuter(inner: LocalInner)
    object LocalOuter {
      val BsonFields = generateBsonFields[LocalOuter]
      val JsonFields = generateJsonFields[LocalOuter]
    }
    assertEquals(LocalOuter.BsonFields.inner.v, "inner.b_val")
    assertEquals(LocalOuter.JsonFields.inner.v, "inner.j_val")
  }

  test("conflict inside nested type fails compilation (Scala 3)") {
    val errors = compileErrors("""
      case class InnerBad(
        @org.mongodb.scala.bson.annotations.BsonProperty("b1")
        @zio.bson.bsonField("b2")
        v: String
      )
      case class OuterBad(inner: InnerBad)
      object OuterBad {
        val Fields = generateFields[OuterBad]
      }
    """)
    assert(errors.contains("conflicting BSON renaming annotations"), errors)
  }

  test("conflict split between constructor parameter and field (Scala 3)") {
    import scala.annotation.meta.{field, param}
    val errors = compileErrors("""
      case class SplitBad(
        @(org.mongodb.scala.bson.annotations.BsonProperty @field)("b1")
        @(zio.bson.bsonField @param)("b2")
        name: String
      )
      object SplitBad {
        val Fields = generateFields[SplitBad]
      }
    """)
    assert(errors.contains("conflicting BSON renaming annotations"), errors)
  }

  test("definitions from Scala3Definitions") {
    assertEquals(Scala3Definitions.S3FormatBson.BsonFields.name, "b")
    assertEquals(Scala3Definitions.S3FormatJson.JsonFields.name, "j")
  }

  test("nested type from earlier compile run preserves JSON in Scala 3") {
    case class S3TestOwner(inner: SharedNestedDual)
    object S3TestOwner {
      val BsonFields = generateBsonFields[S3TestOwner]
      val JsonFields = generateJsonFields[S3TestOwner]
    }
    assertEquals(S3TestOwner.BsonFields.inner.code, "inner.b_code")
    assertEquals(S3TestOwner.JsonFields.inner.code, "inner.j_code")
  }
}
