---
sidebar_position: 4
---

# Scala 2

On Scala 2.13 the `@moka` macro annotation does everything by itself — it
generates the companion object (or extends an existing one) with the `Fields`
object. Requires the `-Ymacro-annotations` compiler flag.

```scala
import io.moka._

@moka
case class Simple(color: String)

Simple.Fields.color == "color"
```

An existing companion object is preserved:

```scala
@moka
case class WithCompanion(a: Int)
object WithCompanion {
  val default: WithCompanion = WithCompanion(0)
}

WithCompanion.Fields.a == "a"
```

The generated object can be renamed through the annotation argument,
`@moka("Params")` or `@moka(name = "Params")`:

```scala
@moka("Params")
final case class Renamed(a: Int)

Renamed.Params.a == "a"
```

A member of your own companion with the name of a generated object is a compile
error.

## Format-specific annotations

Besides `@moka` (which generates `Fields` in BSON mode), Scala 2 provides dedicated annotations:

- `@mokaBson`: generates `BsonFields` by default, inspecting only BSON annotations (`@BsonProperty`, `@bsonField`).
- `@mokaJson`: generates `JsonFields` by default, inspecting zio-json's `@jsonField`.

```scala
@mokaJson
case class Payload(@jsonField("user_id") userId: String)

Payload.JsonFields.userId == "user_id"
```

### Projecting both BSON and JSON

To expose both formats from the same case class, declare placeholder vals in the companion (recommended for cross-compiling with Scala 3), or stack format annotations:

```scala
@moka
case class Dual(
    _id: String,
    @BsonProperty("b_col") @jsonField("color") color: String
)
object Dual {
  val BsonFields = generateBsonFields[Dual]
  val JsonFields = generateJsonFields[Dual]
}

Dual.BsonFields.color == "b_col"
Dual.JsonFields.color == "color"
```

When annotations are stacked, the first one to expand handles the whole stack:
with placeholder vals it generates exactly those objects (the annotations'
names are ignored); without, one object per annotation — `@mokaBson @mokaJson`
gives `BsonFields` and `JsonFields`.

## Nested fields

The annotation descends into nested case classes exactly as the cross-compiled
style does, with or without a companion object of your own — but **every nested
case class needs its own moka object** (since 0.2.0):

```scala
@moka
final case class Engine(@bsonField("hp") power: Int)

object Model {
  @moka
  final case class Car(engine: Engine, wheels: List[Engine])
}

Model.Car.Fields.engine.power          == "engine.hp"
Model.Car.Fields.wheels._matched.power == "wheels.$.hp"
```

The outer object takes every nested name from `Engine`'s own object, built where
`Engine`'s annotations were visible. That is the point: zio-bson's `@bsonField`
and zio-json's `@jsonField` extend plain `Annotation`, which scalac 2.13 does not
write into classfiles, so re-reading them from another module, a jar, or after
an incremental build would silently produce `"engine.power"`. Composing from
the nested object keeps the rename everywhere.

- **Match the format.** Under `@mokaJson` the nested type needs a JSON object
  (`@mokaJson`, or `@moka` + `val JsonFields = generateJsonFields[Engine]`). A
  type without any rename gets a *neutral* object, valid for every format.
- **The object is found by its format, not its name**, so `@moka("Paths")` on
  the nested type works too. Two objects of one format must have identical
  members, or the outer fails as ambiguous.
- **Missing object → compile error** listing every nested type that needs one,
  with its path: `moka: nested types without a BSON moka object ...
  - Engine (at engine): add @moka`. Objects compiled by moka 0.1.x carry no
  format marker and do not count: recompile them.
- **Leaves need nothing**: value classes, `String` and other plain values, and
  `Map` fields are never descended into.
- **Not beside the annotated class.** The nested type may not be a member of
  the same object or class as the annotated case class, because the annotation
  macro runs before the typer and cannot see it — note where `Engine` is
  declared above. See [cross-compilation](cross.md#nested-types-on-scala-2).

### Opting out

Generic nested case classes (`Box[T]`) cannot carry a moka object, and a type
from a library you do not control may have none. For those, compile with:

```scala
scalacOptions += "-Xmacro-settings:moka.descendUnannotated"
```

A nested type without an object then has its annotations read directly, with a
warning naming it. Renames scalac dropped from the classfile — `@bsonField` and
`@jsonField` on a type compiled in another run — stay lost; `@BsonProperty`, a
Java annotation, survives. Types that do have an object are still composed. The
setting is global to the module, not limited to one type, so prefer giving types
their own object wherever you can.

## Full code

Every supported case is covered by the test suite:

- [Scala2Definitions.scala](https://github.com/vimalaguti/moka/blob/master/examples/src/main/scala-2/io/moka/Scala2Definitions.scala) — the annotated case classes
- [Scala2MokaSpec.scala](https://github.com/vimalaguti/moka/blob/master/examples/src/test/scala-2/io/moka/Scala2MokaSpec.scala) — the assertions
