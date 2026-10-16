package io.moka.optout

import io.moka._
import org.mongodb.scala.bson.annotations.BsonProperty

/** Declared in this (test) run, without a moka object. */
final case class SameRunInner(@BsonProperty("s") a: Int)

@moka
final case class OptOutOuter(
    plain: PlainInner,
    lost: LostInner,
    obj: ObjectInner,
    box: Box[Int],
    mixed: DirectWithComposed,
    many: List[PlainInner],
    same: SameRunInner
)
object OptOutOuter {
  val Fields = generateFields[OptOutOuter]
}

@moka
final case class UnrenamedOuter(inner: Unrenamed)
object UnrenamedOuter {
  val Fields = generateFields[UnrenamedOuter]
}

@mokaJson
final case class JsonOptOutOuter(inner: PlainInner)
object JsonOptOutOuter {
  val JsonFields = generateJsonFields[JsonOptOutOuter]
}

/** `-Xmacro-settings:moka.descendUnannotated` (spec rule 4): nested types
  * without a moka object have their annotations read, with a warning, instead
  * of failing compilation.
  */
class OptOutSpec extends munit.FunSuite {

  test("a nested type without an object is read from its annotations") {
    val a: "plain.p" = OptOutOuter.Fields.plain.a
    assertEquals(a, "plain.p")
    assertEquals(OptOutOuter.Fields.plain.b, "plain.b")
    assertEquals(OptOutOuter.Fields.plain._path, "plain")
    assertEquals(OptOutOuter.Fields.same.a, "same.s")
  }

  test("collections keep their operators when read directly") {
    assertEquals(OptOutOuter.Fields.many.a, "many.p")
    assertEquals(OptOutOuter.Fields.many._matched.a, "many.$.p")
    assertEquals(OptOutOuter.Fields.many._all.b, "many.$[].b")
  }

  test("a rename the classfile lost is not seen: why the opt-out warns") {
    assertEquals(OptOutOuter.Fields.lost.a, "lost.a")
  }

  test("a nested type with an object is still composed") {
    val a: "obj.zr" = OptOutOuter.Fields.obj.a
    assertEquals(a, "obj.zr")
  }

  test("a generic nested type is read from its annotations") {
    assertEquals(OptOutOuter.Fields.box.value, "box.v")
  }

  test("below a type read directly, a nested object is composed") {
    val a: "mixed.o.zr" = OptOutOuter.Fields.mixed.obj.a
    assertEquals(a, "mixed.o.zr")
  }

  test("an object built by reading annotations is never neutral") {
    assertEquals(UnrenamedOuter.Fields.inner.a, "inner.a")
    assert(UnrenamedOuter.Fields.isInstanceOf[MokaBsonNames])
    assert(!(UnrenamedOuter.Fields: Any).isInstanceOf[MokaNames])
    assert(
      compileErrors("implicitly[UnrenamedOuter.Fields.type <:< MokaNames]")
        .contains("Cannot prove")
    )
  }

  test("the format still selects the annotations read") {
    assertEquals(JsonOptOutOuter.JsonFields.inner.a, "inner.a")
    assert(JsonOptOutOuter.JsonFields.isInstanceOf[MokaJsonNames])
  }
}
