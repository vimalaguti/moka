# moka

Macro that creates a `Fields` object with the names of a case class' fields,
so MongoDB filters and projections don't need hardcoded strings. Misspelled
field names fail at compile time.

Works on **Scala 2.13** and **Scala 3** (3.3 LTS) — including cross-compiled
codebases sharing the same sources.

## Installation

```scala
libraryDependencies += "io.github.vimalaguti" %% "moka" % "<version>"
```

On **Scala 2.13** `@moka` is a macro annotation, so the compiler flag that
expands those is required:

```scala
scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
  case Some((2, 13)) => Seq("-Ymacro-annotations")
  case _             => Nil
})
```

Without it the annotation is silently left in place and every `Fields`
selection fails with `not a member of io.moka.FieldsNotGenerated_AddYmacroAnnotations`.
Scala 3 needs no flags.

## Example

Cross-compiled style (works on both versions):

```scala
import io.moka._

@moka
case class Apple(color: String)
object Apple {
  val Fields = generateFields[Apple]
}

Apple.Fields.color == "color"
Apple.Fields.colour // does not compile
```

Scala 2 only — the annotation generates the companion by itself:

```scala
@moka
case class Apple(color: String)

Apple.Fields.color == "color"
```

Scala 3 only — no annotation needed:

```scala
case class Apple(color: String)
object Apple {
  val Fields = generateFields[Apple]
}
```

## Bson annotations

Fields annotated with `@BsonProperty` (mongo-scala-bson) or `@bsonField`
(zio-bson) keep their Scala name but carry the annotated name as value:

```scala
@moka
case class Fruit(@BsonProperty("c") color: String)
object Fruit {
  val Fields = generateFields[Fruit]
}

Fruit.Fields.color == "c"
```

## Nested fields

A field whose type is another case class becomes a path node exposing that
type's fields, each carrying the dotted path MongoDB expects:

```scala
case class Engine(power: Int)
@moka
case class Car(engine: Engine)
object Car {
  val Fields = generateFields[Car]
}

Car.Fields.engine.power == "engine.power"
Car.Fields.engine._path == "engine"
```

`Option` and collections are transparent (`List[Engine]` gives the same path).
A collection of case classes also exposes MongoDB's array operators:

```scala
case class Bike(wheels: List[Engine])

Bike.Fields.wheels._matched.power == "wheels.$.power"   // positional $
Bike.Fields.wheels._all.power     == "wheels.$[].power" // $[]
```

Every generated member starts with an underscore (`_path`, `_matched`, `_all`),
so it can never collide with a field of your own. On Scala 2 the nested type must
be declared outside the enclosing object — see the
[cross-compilation](docs/cross.md) page.

## Usage idea

When using mongodb, you are required to set the field name when filtering or
for projections. With this macro you can avoid setting the name manually and
instead use the `Fields` object.

## License

Apache-2.0. Copyright 2026 Vittorio Malaguti.
