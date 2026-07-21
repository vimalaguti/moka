# moka

Macro that creates a `Fields` object with the names of a case class' fields,
so MongoDB filters and projections don't need hardcoded strings. Misspelled
field names fail at compile time.

Works on **Scala 2.13** and **Scala 3** (3.3 LTS) — including cross-compiled
codebases sharing the same sources.

## Example

Cross-compiled style (works on both versions):

```scala
import io.moka.*

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

## Usage idea

When using mongodb, you are required to set the field name when filtering or
for projections. With this macro you can avoid setting the name manually and
instead use the `Fields` object.
