package io.moka.optout

import io.moka._

/** Review claim 2.1: a generic nested type instantiated with a descendable type
  * descends into it, as Scala 3 does.
  */
@moka
final case class BoxedObjectOuter(box: Box[PlainInner])
object BoxedObjectOuter {
  val Fields = generateFields[BoxedObjectOuter]
}

/** Review claim 3b: a meta-annotated rename on a nested plain type. */
@moka
final case class MetaOuter(inner: MetaInner)
object MetaOuter {
  val Fields = generateFields[MetaOuter]
}

class ReviewOptOutSpec extends munit.FunSuite {

  test("2.1: Box[PlainInner] descends into PlainInner") {
    assertEquals(
      compileErrors(
        """val x: "box.v.p" = BoxedObjectOuter.Fields.box.value.a"""
      ),
      ""
    )
  }

  // With @field the annotation lands on the field symbol, not the parameter.
  test("3b: a nested type's meta-annotated rename is read") {
    assertEquals(
      compileErrors("""val x: "inner.m" = MetaOuter.Fields.inner.a"""),
      ""
    )
  }
}
