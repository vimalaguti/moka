package io.moka

import zio.json.jsonField

/* Outer types of the composition contract
 * (specs/specs/2026-10-16-nested-composition-design.md). Each nested type is
 * compiled in the main run, so on Scala 2.13 it is seen here only through its
 * classfile, where zio-bson/zio-json annotations are gone.
 *
 * Literal-type checks that are wrong on 2.13 today go through compileErrors:
 * written directly, they would break the whole suite's compilation. */

@moka
final case class ZioJsonCrossRunOuter(inner: SharedZioJsonInner)
object ZioJsonCrossRunOuter {
  val JsonFields = generateJsonFields[ZioJsonCrossRunOuter]
}

@moka
final case class CustomNameCrossRunOuter(inner: SharedCustomNamedInner)
object CustomNameCrossRunOuter {
  val Fields = generateFields[CustomNameCrossRunOuter]
}

@mokaJson
final case class NeutralJsonOuter(inner: SharedNeutralInner)
object NeutralJsonOuter {
  val JsonFields = generateJsonFields[NeutralJsonOuter]
}

@moka
final case class ComposedBasket(
    items: List[ComposedItem],
    maybe: Option[ComposedItem]
)
object ComposedBasket {
  val Fields = generateFields[ComposedBasket]
}

@moka
final case class ComposedTop(
    @zio.bson.bsonField("m") mid: ComposedMid,
    mids: List[ComposedMid]
)
object ComposedTop {
  val Fields = generateFields[ComposedTop]
}

@moka
final case class DuplicateObjectsOuter(inner: SharedDuplicateObjects)
object DuplicateObjectsOuter {
  val Fields = generateFields[DuplicateObjectsOuter]
}

/** `A` is a value class without any moka object: always a leaf. */
@moka
final case class ValueClassLeaves(id: A, maybe: Option[A], many: List[A])
object ValueClassLeaves {
  val Fields     = generateFields[ValueClassLeaves]
  val JsonFields = generateJsonFields[ValueClassLeaves]
}

@mokaJson
final case class JsonRenamedOnly(@jsonField("j") a: String)
object JsonRenamedOnly {
  val JsonFields = generateJsonFields[JsonRenamedOnly]
}

class NestedCompositionSpec extends munit.FunSuite {

  /** The markers a generated object's runtime class implements. */
  private def markers(o: Any): Set[String] =
    Set(
      if (o.isInstanceOf[MokaNames]) Some("neutral") else None,
      if (o.isInstanceOf[MokaBsonNames]) Some("bson") else None,
      if (o.isInstanceOf[MokaJsonNames]) Some("json") else None
    ).flatten

  /** A negative subtype check must fail for that reason, not for a typo. */
  private def assertCannotProve(errors: String): Unit =
    assert(errors.contains("Cannot prove"), errors)

  // (a)
  test("composition across runs: zio-bson rename reaches the outer path") {
    assertEquals(SharedZioBsonInner.Fields.a, "zr")
    assertEquals(ZioBsonCrossRunOuter.Fields.inner._path, "inner")
    assertEquals(ZioBsonCrossRunOuter.Fields.inner.a, "inner.zr")
    assertEquals(
      compileErrors(
        """val a: "inner.zr" = ZioBsonCrossRunOuter.Fields.inner.a"""
      ),
      ""
    )
  }

  // (b)
  test("composition across runs: zio-json rename reaches the outer path") {
    assertEquals(SharedZioJsonInner.JsonFields.a, "jr")
    assertEquals(ZioJsonCrossRunOuter.JsonFields.inner.a, "inner.jr")
    assertEquals(
      compileErrors(
        """val a: "inner.jr" = ZioJsonCrossRunOuter.JsonFields.inner.a"""
      ),
      ""
    )
  }

  // (c)
  test("composition finds a nested object with a custom name") {
    assertEquals(SharedCustomNamedInner.Custom.a, "cr")
    assertEquals(CustomNameCrossRunOuter.Fields.inner.a, "inner.cr")
    assertEquals(
      compileErrors(
        """val a: "inner.cr" = CustomNameCrossRunOuter.Fields.inner.a"""
      ),
      ""
    )
  }

  // (d)
  test("a neutral nested object composes into any format") {
    val a: "inner.a" = NeutralJsonOuter.JsonFields.inner.a
    assertEquals(a, "inner.a")
    assertEquals(NeutralJsonOuter.JsonFields.inner._path, "inner")
  }

  test("an object without renames carries only the neutral marker") {
    assertEquals(markers(SharedNeutralInner.Fields), Set("neutral"))
    assertEquals(
      compileErrors("implicitly[SharedNeutralInner.Fields.type <:< MokaNames]"),
      ""
    )
    assertCannotProve(
      compileErrors(
        "implicitly[SharedNeutralInner.Fields.type <:< MokaBsonNames]"
      )
    )
    assertCannotProve(
      compileErrors(
        "implicitly[SharedNeutralInner.Fields.type <:< MokaJsonNames]"
      )
    )
  }

  test("an outer composing only neutral objects is neutral") {
    assertEquals(markers(NeutralJsonOuter.JsonFields), Set("neutral"))
    assertEquals(
      compileErrors(
        "implicitly[NeutralJsonOuter.JsonFields.type <:< MokaNames]"
      ),
      ""
    )
  }

  test("a BSON-renamed object carries only the BSON marker") {
    assertEquals(markers(SharedZioBsonInner.Fields), Set("bson"))
    assertEquals(
      compileErrors(
        "implicitly[SharedZioBsonInner.Fields.type <:< MokaBsonNames]"
      ),
      ""
    )
    assertCannotProve(
      compileErrors("implicitly[SharedZioBsonInner.Fields.type <:< MokaNames]")
    )
    assertCannotProve(
      compileErrors(
        "implicitly[SharedZioBsonInner.Fields.type <:< MokaJsonNames]"
      )
    )
    // generateBsonFields is BSON too
    assertEquals(markers(SharedNestedDual.BsonFields), Set("bson"))
  }

  test("a JSON-renamed object carries only the JSON marker") {
    assertEquals(markers(JsonRenamedOnly.JsonFields), Set("json"))
    assertEquals(
      compileErrors(
        "implicitly[JsonRenamedOnly.JsonFields.type <:< MokaJsonNames]"
      ),
      ""
    )
    assertCannotProve(
      compileErrors("implicitly[JsonRenamedOnly.JsonFields.type <:< MokaNames]")
    )
    assertCannotProve(
      compileErrors(
        "implicitly[JsonRenamedOnly.JsonFields.type <:< MokaBsonNames]"
      )
    )
  }

  test("the placeholder's call, not the annotation, decides the format") {
    // @moka on the class, generateJsonFields in the companion
    assertEquals(markers(SharedZioJsonInner.JsonFields), Set("json"))
    assertEquals(markers(SharedNestedDual.JsonFields), Set("json"))
  }

  test("a rename of any format makes the object non-neutral") {
    // generateFields ignores @jsonField for the name, not for neutrality
    assertEquals(JsonOnlyFruit.Fields.kind, "kind")
    assertEquals(markers(JsonOnlyFruit.Fields), Set("bson"))
  }

  test("a field renamed to its own name is not neutral") {
    assertEquals(SharedSelfRenamed.Fields.a, "a")
    assertEquals(markers(SharedSelfRenamed.Fields), Set("bson"))
    assertCannotProve(
      compileErrors("implicitly[SharedSelfRenamed.Fields.type <:< MokaNames]")
    )
  }

  test("an outer composing a renamed object is not neutral") {
    assertEquals(markers(ZioBsonCrossRunOuter.Fields), Set("bson"))
    assertEquals(markers(ZioJsonCrossRunOuter.JsonFields), Set("json"))
  }

  test("an empty rename is not a rename, so the object stays neutral") {
    assertEquals(markers(SharedEmptyRenames.Fields), Set("neutral"))
    assertEquals(markers(SharedEmptyRenames.JsonFields), Set("neutral"))
    assertEquals(markers(JavaExplicitEmpty.Fields), Set("neutral"))
  }

  // (e)
  test("collections over a composed type: path and array operators") {
    assertEquals(ComposedBasket.Fields.items._path, "items")
    assertEquals(ComposedBasket.Fields.items.qty, "items.q")
    assertEquals(ComposedBasket.Fields.items._matched.qty, "items.$.q")
    assertEquals(ComposedBasket.Fields.items._all.qty, "items.$[].q")
    assertEquals(ComposedBasket.Fields.items._all.note, "items.$[].note")
    assertEquals(
      compileErrors(
        """val q: "items.$.q" = ComposedBasket.Fields.items._matched.qty"""
      ),
      ""
    )
  }

  test("Option over a composed type is transparent, without array operators") {
    assertEquals(ComposedBasket.Fields.maybe.qty, "maybe.q")
    assert(compileErrors("ComposedBasket.Fields.maybe._matched").nonEmpty)
  }

  test("nested-of-nested composition keeps every level's renames") {
    assertEquals(ComposedTop.Fields.mid._path, "m")
    assertEquals(ComposedTop.Fields.mid.item._path, "m.it")
    assertEquals(ComposedTop.Fields.mid.item.qty, "m.it.q")
    assertEquals(ComposedTop.Fields.mid.items.qty, "m.its.q")
    assertEquals(ComposedTop.Fields.mid.items._matched.qty, "m.its.$.q")
    assertEquals(ComposedTop.Fields.mid.items._all._path, "m.its.$[]")
    assertEquals(
      compileErrors("""val q: "m.it.q" = ComposedTop.Fields.mid.item.qty"""),
      ""
    )
  }

  test("an outer collection's operators compose the nested type again") {
    assertEquals(ComposedTop.Fields.mids.item.qty, "mids.it.q")
    assertEquals(ComposedTop.Fields.mids._matched.item.qty, "mids.$.it.q")
    assertEquals(ComposedTop.Fields.mids._all.item.qty, "mids.$[].it.q")
    assertEquals(
      ComposedTop.Fields.mids._all.items._matched.qty,
      "mids.$[].its.$.q"
    )
  }

  // (f)
  test("identical duplicate objects on a nested type are not ambiguous") {
    assertEquals(SharedDuplicateObjects.Fields.a, "d")
    assertEquals(SharedDuplicateObjects.BsonFields.a, "d")
    assertEquals(DuplicateObjectsOuter.Fields.inner.a, "inner.d")
    assertEquals(
      compileErrors(
        """val a: "inner.d" = DuplicateObjectsOuter.Fields.inner.a"""
      ),
      ""
    )
  }

  // (g)
  test("value classes and Options of them are leaves needing no object") {
    val id: "id"       = ValueClassLeaves.Fields.id
    val maybe: "maybe" = ValueClassLeaves.Fields.maybe
    val many: "many"   = ValueClassLeaves.JsonFields.many
    assertEquals(id, "id")
    assertEquals(maybe, "maybe")
    assertEquals(many, "many")
  }

  // rule 5
  test("an empty rename keeps the field name, for every annotation") {
    assertEquals(SharedEmptyRenames.Fields.a, "a")
    assertEquals(SharedEmptyRenames.Fields.b, "b")
    assertEquals(SharedEmptyRenames.JsonFields.c, "c")
  }

  test("an empty rename takes no part in conflict detection") {
    val errors = compileErrors("""
      @moka case class EmptyNoConflict(
        @org.mongodb.scala.bson.annotations.BsonProperty("")
        @zio.bson.bsonField("x")
        c: String
      )
      object EmptyNoConflict {
        val Fields = generateFields[EmptyNoConflict]
      }
      val c: "x" = EmptyNoConflict.Fields.c
    """)
    assertEquals(errors, "")
  }
}
