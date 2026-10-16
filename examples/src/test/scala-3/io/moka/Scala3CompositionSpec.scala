package io.moka

/** A transitive three-type cycle declared in this run, each type with its own
  * object. Forcing companion vals whose types reach the current path would
  * raise a CyclicReference; reading annotations must not.
  */
final case class S3TriA(b: S3TriB, a1: Int)
object S3TriA {
  val Fields = generateFields[S3TriA]
}
final case class S3TriB(c: S3TriC, b1: Int)
object S3TriB {
  val Fields = generateFields[S3TriB]
}
final case class S3TriC(a: S3TriA, c1: Int)
object S3TriC {
  val Fields = generateFields[S3TriC]
}

/** Scala 3 side of the composition contract: it never composes, it reads
  * annotations (specs/specs/2026-10-16-nested-composition-design.md).
  */
class Scala3CompositionSpec extends munit.FunSuite {

  // (l)
  test("a plain nested type from an earlier run still gets renamed paths") {
    case class PlainOuter(inner: S3PlainRenamedInner)
    object PlainOuter {
      val Fields     = generateFields[PlainOuter]
      val JsonFields = generateJsonFields[PlainOuter]
    }
    val zb: "inner.zb" = PlainOuter.Fields.inner.a
    val b: "inner.b"   = PlainOuter.Fields.inner.b
    val a: "inner.a"   = PlainOuter.JsonFields.inner.a
    val zj: "inner.zj" = PlainOuter.JsonFields.inner.b
    assertEquals(zb, "inner.zb")
    assertEquals(b, "inner.b")
    assertEquals(a, "inner.a")
    assertEquals(zj, "inner.zj")
  }

  test("a plain nested type is not an error on Scala 3") {
    val errors = compileErrors("""
      case class PlainList(items: List[S3PlainRenamedInner])
      object PlainList { val Fields = generateFields[PlainList] }
      val q: "items.$.zb" = PlainList.Fields.items._matched.a
    """)
    assertEquals(errors, "")
  }

  // (m)
  test("a two-type cycle stops on the outer's path") {
    val o: "n.o" = S3CycleOuter.Fields.n.o
    assertEquals(o, "n.o")
    assertEquals(S3CycleOuter.Fields.n.y, "n.y")
    val n: "o.n" = S3CycleN.Fields.o.n
    assertEquals(n, "o.n")
    assert(compileErrors("S3CycleOuter.Fields.n.o._path").nonEmpty)
  }

  test("a transitive three-type cycle compiles and stops on the path") {
    val a: "b.c.a" = S3TriA.Fields.b.c.a
    assertEquals(a, "b.c.a")
    assertEquals(S3TriA.Fields.b.c.c1, "b.c.c1")
    val b: "c.a.b" = S3TriB.Fields.c.a.b
    assertEquals(b, "c.a.b")
    assertEquals(S3TriC.Fields.a.b.c, "a.b.c")
  }

  // (n)
  test("opaque type fields are leaves") {
    case class WithOpaque(
        code: S3Opaques.Code,
        maybe: Option[S3Opaques.Code],
        boxed: S3Opaques.Boxed,
        boxes: List[S3Opaques.Boxed]
    )
    object WithOpaque {
      val Fields = generateFields[WithOpaque]
    }
    val code: "code"   = WithOpaque.Fields.code
    val maybe: "maybe" = WithOpaque.Fields.maybe
    val boxed: "boxed" = WithOpaque.Fields.boxed
    val boxes: "boxes" = WithOpaque.Fields.boxes
    assertEquals(code, "code")
    assertEquals(maybe, "maybe")
    assertEquals(boxed, "boxed")
    assertEquals(boxes, "boxes")
  }
}
