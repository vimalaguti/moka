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

// ignoring @jsonField is the point here; Scala 2 sees JsonFields and does not warn
@moka
@scala.annotation.nowarn("msg=moka")
final case class JsonOnlyFruit(
    @jsonField("fruit_kind") kind: String
)
object JsonOnlyFruit {
  val Fields     = generateFields[JsonOnlyFruit]
  val JsonFields = generateJsonFields[JsonOnlyFruit]
}

@moka
final case class ZioBsonCrossRunOuter(inner: SharedZioBsonInner)
object ZioBsonCrossRunOuter {
  val Fields = generateFields[ZioBsonCrossRunOuter]
}

@moka
final case class JavaCrossRunOuter(
    p: SharedJavaInnerPositional,
    n: SharedJavaInnerNamed
)
object JavaCrossRunOuter {
  val Fields = generateFields[JavaCrossRunOuter]
}

@moka
final case class JavaOutOfOrderArgs(
    @org.bson.codecs.pojo.annotations.BsonProperty(
      useDiscriminator = true,
      value = "r"
    ) a: String
)
object JavaOutOfOrderArgs {
  val Fields = generateFields[JavaOutOfOrderArgs]
}

@moka
final case class JavaDiscriminatorOnly(
    @org.bson.codecs.pojo.annotations.BsonProperty(useDiscriminator = true)
    a: String
)
object JavaDiscriminatorOnly {
  val Fields = generateFields[JavaDiscriminatorOnly]
}

@moka
final case class JavaExplicitEmpty(
    @org.bson.codecs.pojo.annotations.BsonProperty("") a: String
)
object JavaExplicitEmpty {
  val Fields = generateFields[JavaExplicitEmpty]
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

  test("cross-compiled Java driver BsonProperty renames field") {
    assertEquals(SharedJavaBson.Fields.f, "jb")
  }

  test("nested zio-bson @bsonField from an earlier compile run renames field") {
    assertEquals(ZioBsonCrossRunOuter.Fields.inner.a, "inner.zr")
    // Through compileErrors: on 2.13 a wrong literal type would otherwise break
    // the whole suite's compilation instead of failing this test.
    assertEquals(
      compileErrors(
        """val a: "inner.zr" = ZioBsonCrossRunOuter.Fields.inner.a"""
      ),
      ""
    )
  }

  // The nested types now carry their own moka object, so on 2.13 this pins
  // that the Java @BsonProperty names reach the outer through composition.
  test("nested Java @BsonProperty from an earlier compile run renames field") {
    assertEquals(JavaCrossRunOuter.Fields.p.a, "p.jp")
    assertEquals(JavaCrossRunOuter.Fields.n.a, "n.jn")
  }

  test("out-of-order named arguments on Java @BsonProperty rename field") {
    assertEquals(JavaOutOfOrderArgs.Fields.a, "r")
  }

  test("Java @BsonProperty with only useDiscriminator keeps field name") {
    assertEquals(JavaDiscriminatorOnly.Fields.a, "a")
  }

  // The Java driver treats an empty value as "use the field name".
  test("Java @BsonProperty(\"\") keeps field name") {
    assertEquals(JavaExplicitEmpty.Fields.a, "a")
  }
}
