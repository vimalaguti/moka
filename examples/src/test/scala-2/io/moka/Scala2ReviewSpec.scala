package io.moka

import org.mongodb.scala.bson.annotations.BsonProperty
import scala.annotation.meta.field

/** Review claim 2.4: a meta-annotated rename must not be ignored. */
@moka
final case class S2MetaAnnotated(@(BsonProperty @field)("b1") a: Int)

/** Review claim 2.5: a field typed as another package's same-named class is not
  * a self-reference.
  */
@moka
final case class User(old: reviewv1.User)

/** Package-level type whose simple name a sibling inside an object shadows
  * (found while compile-checking the client skill).
  */
@moka
final case class ShadowEngine(power: Int)
object ShadowEngine {
  val Fields = generateFields[ShadowEngine]
}

/** Claims from the external review of 7cc5b30 (branch-review.md), each pinned
  * as the behaviour the spec requires: red where the claim is a real bug.
  */
class Scala2ReviewSpec extends munit.FunSuite {

  test("2.3: @moka on a generic case class is a clear error") {
    val errors = compileErrors("@moka case class GenBox[T](value: T)")
    assert(errors.contains("moka:"), errors)
    assert(errors.contains("generic"), errors)
  }

  test("2.3: @moka on a generic case class without T fields is an error too") {
    val errors = compileErrors("@moka case class Tagged[T](id: String)")
    assert(errors.contains("generic"), errors)
  }

  test("2.4: meta-annotated @(BsonProperty @field) renames the field") {
    assertEquals(
      compileErrors("""val x: "b1" = S2MetaAnnotated.Fields.a"""),
      ""
    )
  }

  test("2.5: a same-named class from another package is descended into") {
    assertEquals(
      compileErrors("""val x: "old.n" = User.Fields.old.name"""),
      ""
    )
  }

  test(
    "2.5: a self-reference qualified by its own package or object is a leaf"
  ) {
    val diags = CompileHarness.compile("Qualified.scala" -> """
      package qself {
        import io.moka._
        @moka final case class Event(a: Option[qself.Event], b: Option[_root_.qself.Event])
        object Outer {
          @moka final case class Event(a: Option[Outer.Event], b: Option[qself.Outer.Event])
        }
        object Use {
          val a: "a" = Event.Fields.a
          val b: "b" = Event.Fields.b
          val c: "a" = Outer.Event.Fields.a
          val d: "b" = Outer.Event.Fields.b
        }
      }
    """)
    assertEquals(diags, Nil)
  }

  test("sibling shadowing a package-level type is an error, not its names") {
    val errors = compileErrors("""
      object ShadowModel {
        final case class ShadowEngine(torque: Int)
        @moka final case class Car(engine: ShadowEngine)
      }
    """)
    assert(errors.contains("moka:"), errors)
  }

  test("stacked annotations generating the same default name are an error") {
    val errors = compileErrors("@moka @moka(\"Fields\") case class Dup(a: Int)")
    assert(errors.contains("more than once"), errors)
  }
}
