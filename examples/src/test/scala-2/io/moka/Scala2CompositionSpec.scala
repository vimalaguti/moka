package io.moka

import org.mongodb.scala.bson.annotations.BsonProperty
import zio.json.jsonField

/** Declared in this (test) run, without a moka object: the rule-4 error must
  * not depend on whether the nested type comes from source or a classfile.
  */
final case class S2SameRunPlain(@BsonProperty("p") a: Int)

/** Generic: it cannot carry a moka object, so it is never composed. */
final case class S2GenericInner[T](@BsonProperty("g") value: T)

/** Stacked annotations, no companion: each generates its object into one real
  * companion (spec rule 6).
  */
@mokaBson
@mokaJson
final case class S2StackedNoCompanion(
    @BsonProperty("b") @jsonField("j") name: String
)

/** A chain with no moka objects: all of them must be reported at once. */
final case class S2MissDeep(x: Int)
final case class S2MissMid(deep: S2MissDeep)
final case class S2MissOther(y: Int)

/** 2.13-only rules of the composition contract
  * (specs/specs/2026-10-16-nested-composition-design.md).
  */
class Scala2CompositionSpec extends munit.FunSuite {

  // (h)
  test("a nested type from a classfile without a moka object is an error") {
    val errors = compileErrors("""
      @moka case class UsesClassfilePlain(inner: S2ClassfilePlain)
      object UsesClassfilePlain {
        val Fields = generateFields[UsesClassfilePlain]
      }
    """)
    assert(errors.contains("moka:"), errors)
    assert(errors.contains("S2ClassfilePlain"), errors)
    assert(errors.contains("BSON"), errors)
    assert(errors.contains("@moka"), errors)
    assert(errors.contains("moka.descendUnannotated"), errors)
  }

  test("a nested type from the same run without a moka object is an error") {
    val errors = compileErrors("""
      @moka case class UsesSameRunPlain(inner: S2SameRunPlain)
    """)
    assert(errors.contains("moka:"), errors)
    assert(errors.contains("S2SameRunPlain"), errors)
    assert(errors.contains("BSON"), errors)
    assert(errors.contains("@moka"), errors)
    assert(errors.contains("moka.descendUnannotated"), errors)
  }

  test("the error applies inside Option and collections too") {
    val errors = compileErrors("""
      @moka case class UsesPlainList(items: List[Option[S2ClassfilePlain]])
    """)
    assert(errors.contains("moka:"), errors)
    assert(errors.contains("S2ClassfilePlain"), errors)
  }

  test("an object without a marker does not count (e.g. built by 0.1.x)") {
    val errors = compileErrors("""
      @moka case class UsesUnmarked(inner: S2UnmarkedInner)
    """)
    assert(errors.contains("moka:"), errors)
    assert(errors.contains("S2UnmarkedInner"), errors)
    assert(errors.contains("moka.descendUnannotated"), errors)
  }

  test("JSON outer over a nested type with only a renamed BSON object") {
    val errors = compileErrors("""
      @mokaJson case class JsonOverBson(inner: SharedZioBsonInner)
    """)
    assert(errors.contains("moka:"), errors)
    assert(errors.contains("SharedZioBsonInner"), errors)
    assert(errors.contains("JSON"), errors)
    assert(errors.contains("@mokaJson"), errors)
    assert(errors.contains("moka.descendUnannotated"), errors)
  }

  test("BSON outer over a nested type with only a renamed JSON object") {
    val errors = compileErrors("""
      @moka case class BsonOverJson(n: Nutrition)
    """)
    assert(errors.contains("moka:"), errors)
    assert(errors.contains("Nutrition"), errors)
    assert(errors.contains("BSON"), errors)
    assert(errors.contains("moka.descendUnannotated"), errors)
  }

  test("a generic nested type cannot be composed, so it is an error") {
    val errors = compileErrors("""
      @moka case class UsesGeneric(inner: S2GenericInner[Int])
    """)
    assert(errors.contains("moka:"), errors)
    assert(errors.contains("S2GenericInner"), errors)
    assert(errors.contains("generic"), errors)
    assert(errors.contains("moka.descendUnannotated"), errors)
  }

  test("a value class field needs no moka object") {
    val errors = compileErrors("""
      @moka case class UsesValueClass(id: A, ids: List[Option[A]])
      val id: "id" = UsesValueClass.Fields.id
    """)
    assertEquals(errors, "")
  }

  // (i)
  test("differing objects of one format on a nested type are ambiguous") {
    assertEquals(S2AmbiguousInner.Fields.a, "a")
    assertEquals(S2AmbiguousInner.Manual.a, "x")
    val errors = compileErrors("""
      @moka case class UsesAmbiguous(inner: S2AmbiguousInner)
    """)
    assert(errors.contains("moka:"), errors)
    assert(errors.contains("ambiguous"), errors)
    assert(errors.contains("S2AmbiguousInner"), errors)
    assert(errors.contains("Fields"), errors)
    assert(errors.contains("Manual"), errors)
  }

  // (j)
  test("stacked annotations without a companion yield loadable objects") {
    // Constant-folded selections never load the objects' classes.
    assertEquals(S2StackedNoCompanion.BsonFields.name, "b")
    assertEquals(S2StackedNoCompanion.JsonFields.name, "j")
    // Used as values they must exist at runtime. A missing class surfaces as
    // NoClassDefFoundError, a LinkageError munit treats as fatal, so catch it.
    def load(o: => AnyRef): Either[Throwable, AnyRef] =
      try Right(o)
      catch { case t: Throwable => Left(t) }
    val bson = load(S2StackedNoCompanion.BsonFields)
    val json = load(S2StackedNoCompanion.JsonFields)
    assert(bson.exists(_ ne null), bson)
    assert(json.exists(_ ne null), json)
    assert(bson.exists(_.isInstanceOf[MokaBsonNames]), bson)
    assert(json.exists(_.isInstanceOf[MokaJsonNames]), json)
  }

  test("every nested type missing a moka object is reported in one error") {
    val errors = compileErrors("""
      @moka case class UsesMissing(mid: S2MissMid, other: Option[S2MissOther])
    """)
    assert(errors.contains("S2MissMid"), errors)
    assert(errors.contains("S2MissOther"), errors)
    // reached only through S2MissMid, which has no object to compose
    assert(errors.contains("S2MissDeep"), errors)
    assert(errors.contains("mid.deep"), errors)
  }

  test("a nested type whose own expansion failed is not told to add @moka") {
    val diags = CompileHarness.compile("Casc.scala" -> """
      package casc
      import io.moka._

      final case class Leaf(x: Int)

      @moka final case class Mid(leaf: Leaf)
      object Mid { val Fields = generateFields[Mid] }

      @moka final case class MidNoCompanion(leaf: Leaf)

      @moka final case class Outer(mid: Mid, other: MidNoCompanion)
      object Outer { val Fields = generateFields[Outer] }
    """)
    val errors = diags.filter(_.severity == "ERROR").map(_.msg)
    val outer =
      errors.find(_.contains("(at mid)")).getOrElse(fail(errors.mkString("\n")))
    assert(
      outer.contains(
        "Mid (at mid): it carries a moka annotation, but its own expansion failed"
      ),
      outer
    )
    assert(
      outer.contains("MidNoCompanion (at other): it carries a moka annotation"),
      outer
    )
    // the real cause is reported at Mid itself
    assert(errors.exists(_.contains("Leaf (at leaf): add @moka")), errors)
    // a failed expansion stops there: the outer error does not repeat Leaf
    assert(!outer.contains("Leaf"), outer)
  }

  test(
    "an object ignoring the only renames there are warns, unless both formats are generated"
  ) {
    val diags = CompileHarness.compile("Warn.scala" -> """
      package warn
      import io.moka._
      import zio.json.jsonField

      @moka final case class JsonOnly(@jsonField("j") a: String)

      @moka final case class Both(@jsonField("j") a: String)
      object Both {
        val Fields     = generateFields[Both]
        val JsonFields = generateJsonFields[Both]
      }

      @mokaJson final case class JsonAnn(@jsonField("j") a: String)
    """)
    val warnings = diags.filter(_.severity == "WARNING").map(_.msg)
    assert(diags.forall(_.severity != "ERROR"), diags)
    assertEquals(
      warnings.count(_.contains("renamed only by JSON")),
      1,
      warnings
    )
    assert(warnings.exists(_.contains("JsonOnly's fields")), warnings)
    assert(warnings.exists(_.contains("generateJsonFields")), warnings)
  }
}
