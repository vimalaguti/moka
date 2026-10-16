package mokaconsumer

import io.moka.*
import zio.bson.bsonField

/* Every other fixture lives in package io.moka, where package-private
 * definitions are visible. The macro expansion here must not depend on that:
 * the per-marker root classes are private[moka]. */

final case class ExtInner(@bsonField("x") a: String)
final case class ExtOuter(inner: ExtInner, plain: String)
object ExtOuter {
  val Fields     = generateFields[ExtOuter]
  val JsonFields = generateJsonFields[ExtOuter]
}

final case class ExtPlain(a: String)
object ExtPlain {
  val Fields = generateFields[ExtPlain]
}

class ExternalConsumerSpec extends munit.FunSuite {

  test("a consumer outside io.moka gets marked roots and literal paths") {
    val a: "inner.x" = ExtOuter.Fields.inner.a
    assertEquals(a, "inner.x")
    val bson: FieldNames & MokaBsonNames = ExtOuter.Fields
    val json: FieldNames & MokaJsonNames = ExtOuter.JsonFields
    val neutral: FieldNames & MokaNames  = ExtPlain.Fields
    assert(bson.isInstanceOf[MokaBsonNames])
    assert(json.isInstanceOf[MokaJsonNames])
    assert(neutral.isInstanceOf[MokaNames])
  }

  test("markers can be matched at runtime, and nested nodes carry none") {
    def kind(o: Any): String = o match
      case _: MokaNames     => "neutral"
      case _: MokaBsonNames => "bson"
      case _: MokaJsonNames => "json"
      case _                => "none"
    assertEquals(kind(ExtOuter.Fields), "bson")
    assertEquals(kind(ExtOuter.JsonFields), "json")
    assertEquals(kind(ExtPlain.Fields), "neutral")
    assertEquals(kind(ExtOuter.Fields.inner), "none")
  }
}
