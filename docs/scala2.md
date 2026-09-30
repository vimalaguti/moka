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

The generated object can be renamed through the annotation argument:

```scala
@moka("Params")
final case class Renamed(a: Int)

Renamed.Params.a == "a"
```

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

## Nested fields

The annotation descends into nested case classes exactly as the cross-compiled
style does, with or without a companion object of your own:

```scala
final case class Engine(power: Int)

object Model {
  @moka
  final case class Car(engine: Engine, wheels: List[Engine])
}

Car.Fields.engine.power          == "engine.power"
Car.Fields.wheels._matched.power == "wheels.$.power"
```

Note where `Engine` is declared. Scala 2 has two restrictions Scala 3 does not:
1. The nested type may **not** be a member of the same object or class as the
   annotated case class, because the annotation macro runs before the typer and
   cannot see it. moka fails with an error naming the type. See
   [cross-compilation](cross.md) for the details.
2. **ZIO JSON `@jsonField` across compilation runs**: zio-json's `@jsonField`
   extends plain `scala.annotation.Annotation` rather than `StaticAnnotation`, so
   `scalac` 2.13 discards it during bytecode generation. If a nested type is
   compiled in a separate compilation run (e.g. in a separate module or library),
   `@jsonField` is erased and `JsonFields` will use the Scala field name. (Java annotations
   like `@BsonProperty` do not suffer from this issue).

## Full code

Every supported case is covered by the test suite:

- [Scala2Definitions.scala](https://github.com/vimalaguti/moka/blob/master/examples/src/main/scala-2/io/moka/Scala2Definitions.scala) — the annotated case classes
- [Scala2MokaSpec.scala](https://github.com/vimalaguti/moka/blob/master/examples/src/test/scala-2/io/moka/Scala2MokaSpec.scala) — the assertions
