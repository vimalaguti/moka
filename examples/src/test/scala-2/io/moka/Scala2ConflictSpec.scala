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

// One object per format: S2LocalOuter composes it in both.
@moka
final case class S2LocalInner(
    @BsonProperty("b_val") @jsonField("j_val") v: String
)
object S2LocalInner {
  val Fields     = generateFields[S2LocalInner]
  val JsonFields = generateJsonFields[S2LocalInner]
}

@moka
final case class S2LocalOuter(inner: S2LocalInner)
object S2LocalOuter {
  val BsonFields = generateBsonFields[S2LocalOuter]
  val JsonFields = generateJsonFields[S2LocalOuter]
}

@moka
final case class S2TestOwner(inner: SharedNestedDual)
object S2TestOwner {
  val BsonFields = generateBsonFields[S2TestOwner]
  val JsonFields = generateJsonFields[S2TestOwner]
}

@mokaBson
@mokaJson
final case class S2StackedCustomNames(
    @BsonProperty("b") @jsonField("j") name: String
)
object S2StackedCustomNames {
  val Bson = generateBsonFields[S2StackedCustomNames]
  val Json = generateJsonFields[S2StackedCustomNames]
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

  test("chosen format inside nested types on Scala 2 (same compile run)") {
    assertEquals(S2LocalOuter.BsonFields.inner.v, "inner.b_val")
    assertEquals(S2LocalOuter.JsonFields.inner.v, "inner.j_val")
  }

  // An outer type no longer re-reads a nested type's annotations, so the
  // conflict in S2NestedConflictInner can only surface when that type's own
  // moka object is generated; through the outer, the missing object is reported.
  test("conflict inside nested type fails compilation (Scala 2)") {
    val errors = compileErrors("""
      @moka case class OuterBad(inner: S2NestedConflictInner)
    """)
    assert(errors.contains("moka:"), errors)
    assert(errors.contains("S2NestedConflictInner"), errors)
    assert(errors.contains("moka.descendUnannotated"), errors)
    assert(!errors.contains("conflicting"), errors)
    val own = compileErrors("""
      @moka case class InnerBad(
        @org.mongodb.scala.bson.annotations.BsonProperty("b1")
        @org.bson.codecs.pojo.annotations.BsonProperty("b2")
        name: String
      )
      object InnerBad { val Fields = generateFields[InnerBad] }
    """)
    assert(own.contains("conflicting BSON renaming annotations"), own)
  }

  test("definitions from Scala2Definitions") {
    assertEquals(Scala2Definitions.S2FormatBson.BsonFields.name, "b")
    assertEquals(Scala2Definitions.S2FormatJson.JsonFields.name, "j")
  }

  test("nested type from same compile run preserves both formats") {
    assertEquals(SharedOuterDual.BsonFields.inner.code, "inner.b_code")
    assertEquals(SharedOuterDual.JsonFields.inner.code, "inner.j_code")
  }

  // Was "drops zio-json @jsonField" with "inner.code": composition reads the
  // name from SharedNestedDual.JsonFields, built where @jsonField was visible.
  test(
    "nested type from earlier compile run keeps zio-json @jsonField on Scala 2"
  ) {
    assertEquals(S2TestOwner.BsonFields.inner.code, "inner.b_code")
    assertEquals(S2TestOwner.JsonFields.inner.code, "inner.j_code")
  }

  test("Java BsonProperty supports nested types in Scala 2") {
    @moka case class JavaOuter(inner: S2JavaInner)
    assertEquals(JavaOuter.Fields.inner.f, "inner.inner_renamed")
  }

  test("Scala 2 supports named arguments on top level annotations") {
    @moka case class NamedArgTop(
        @org.bson.codecs.pojo.annotations.BsonProperty(value = "top_renamed")
        f: String
    )
    assertEquals(NamedArgTop.Fields.f, "top_renamed")
  }

  test("stacking @moka and @mokaBson with placeholders succeeds on Scala 2") {
    @moka
    @mokaBson
    case class StackedPlaceholders(a: Int)
    object StackedPlaceholders {
      val Fields     = generateFields[StackedPlaceholders]
      val BsonFields = generateBsonFields[StackedPlaceholders]
    }
    assertEquals(StackedPlaceholders.Fields.a, "a")
    assertEquals(StackedPlaceholders.BsonFields.a, "a")
  }

  test("stacking with custom placeholder names generates only those objects") {
    assertEquals(S2StackedCustomNames.Bson.name, "b")
    assertEquals(S2StackedCustomNames.Json.name, "j")
    val errors = compileErrors("S2StackedCustomNames.JsonFields")
    assert(
      errors.contains("JsonFields"),
      "unrequested JsonFields was generated"
    )
  }

  test("@moka(name = ...) accepts a named argument") {
    val errors = compileErrors("""
      @moka(name = "Custom") case class NamedObjectArg(a: Int)
      val a: "a" = NamedObjectArg.Custom.a
    """)
    assertEquals(errors, "")
  }

  test(
    "a hand-written companion object Fields is reported, not silently kept"
  ) {
    val errors = compileErrors("""
      @moka case class HandWritten(a: Int)
      object HandWritten { object Fields { val custom = 1 } }
      HandWritten.Fields.custom
    """)
    assert(errors.contains("moka:"), errors)
    assert(errors.contains("Fields"), errors)
  }
}

@moka
case class S2JavaInner(
    @org.bson.codecs.pojo.annotations.BsonProperty("inner_renamed")
    f: String
)
object S2JavaInner {
  val Fields = generateFields[S2JavaInner]
}
