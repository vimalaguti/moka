---
sidebar_position: 2
---

# Features

The snippets on this page use Scala 3. Everything works identically on
Scala 2.13 and in cross-compiled sources — only the way `Fields` is declared
changes, see the [Scala 2](scala2.md) and [cross-compilation](cross.md)
pages.

## What it does

For every field of a case class, the generated `Fields` object exposes a
member with the same name whose **value** is the field's name (or its bson
name, see below). Misspelled names fail at compile time.

```scala mdoc
import io.moka.*

case class Apple(color: String, ripe: Boolean)
object Apple {
  val Fields = generateFields[Apple]
}

Apple.Fields.color
Apple.Fields.ripe
```

```scala mdoc:fail
Apple.Fields.riipe
```

## Literal types

Each member is typed with the **literal type** of its value, not just
`String` — the field name is known to the compiler:

```scala mdoc
val name: "color" = Apple.Fields.color
```

## Renaming the Fields object

The generated object can have any name — it is simply the name of the val:

```scala mdoc
case class Renamed(a: Int)
object Renamed {
  val Params = generateFields[Renamed]
}

Renamed.Params.a
```

(On Scala 2 the name is passed to the annotation instead: `@moka("Params")`.)

## Bson annotations

When a field is renamed in its bson representation, the `Fields` member
keeps the Scala name but carries the **bson name as value** — so queries use
the name that is actually in the database. Both the official mongo driver
annotation and zio-bson are supported:

```scala mdoc
import org.mongodb.scala.bson.annotations.BsonProperty
import zio.bson.bsonField

case class Fruit(@BsonProperty("c") color: String, @bsonField("w") weight: Double)
object Fruit {
  val Fields = generateFields[Fruit]
}

Fruit.Fields.color
Fruit.Fields.weight
```

## Nested fields

When a field's type is another case class, its `Fields` member is not a plain
name but a **path node**: it exposes the nested type's own fields, each carrying
the full dotted path MongoDB expects.

```scala mdoc
case class Engine(@BsonProperty("hp") power: Int)
case class Car(engine: Engine, plate: String)
object Car {
  val Fields = generateFields[Car]
}

Car.Fields.engine.power
Car.Fields.plate
```

Literal types survive the descent:

```scala mdoc
val hp: "engine.hp" = Car.Fields.engine.power
```

### Generated members

Every member moka generates starts with an underscore. Anything *without* one is
a field of yours, so the two can never collide — a case class with a field called
`matched` sits happily next to the `_matched` operator.

| Member     | Appears on       | MongoDB | Meaning                            |
| ---------- | ---------------- | ------- | ---------------------------------- |
| `_path`    | every node       | —       | the node's own dotted path         |
| `_matched` | collection nodes | `$`     | the first element the query matched |
| `_all`     | collection nodes | `$[]`   | every element                      |

```scala mdoc
Car.Fields.engine._path
```

`_matched` and `_all` appear **only** on a field whose type is a collection of
case classes. An `Option` does not get them — an optional sub-document is not an
array — and neither does a collection of a non-case-class type such as
`List[String]`, which stays a plain leaf. They also do not nest: an operator hop
is not itself an array, so `_matched._matched` is a compile error.

`Option` and collections are transparent, because MongoDB uses the same dotted
path whether a sub-document is optional, inside an array, or neither:

```scala mdoc
case class Garage(cars: List[Car], spare: Option[Engine])
object Garage {
  val Fields = generateFields[Garage]
}

Garage.Fields.cars.plate
Garage.Fields.spare.power
```

### Array operators

A field holding a *collection* of case classes also exposes MongoDB's array
operators. `Option` fields do not get them — an optional sub-document is not an
array:

```scala mdoc
case class Wheel(@BsonProperty("d") diameter: Int)
case class Bike(wheels: List[Wheel])
object Bike {
  val Fields = generateFields[Bike]
}

Bike.Fields.wheels.diameter
Bike.Fields.wheels._matched.diameter
Bike.Fields.wheels._all.diameter
```

Which is what makes an array update expressible without string literals. To bump
the wheel a query just matched, use `_matched`:

```scala mdoc
import org.mongodb.scala.bson.collection.immutable.Document

val query = Document(Bike.Fields.wheels.diameter -> 26)
val bumpMatched =
  Document("$set" -> Document(Bike.Fields.wheels._matched.diameter -> 27))
```

To bump every wheel regardless of what matched, use `_all`:

```scala mdoc
val bumpAll =
  Document("$set" -> Document(Bike.Fields.wheels._all.diameter -> 27))
```

Rename `diameter`, or change its `@BsonProperty`, and all three of those stop
compiling rather than silently matching nothing.

Descent stops at value classes (stored flattened, so the path is the outer
field's), at `Map` (naming a value needs a key), and at any type already on the
path, so recursive models terminate. On Scala 2 there is one extra rule about
where the nested type may be declared — see
[cross-compilation](cross.md#nested-types-on-scala-2).

## Using a node as a string

A node is not a `String` — `String` is final, so a value cannot both *be* the
name `"engine"` and expose `.power`. Its own path is `path`, which is what
MongoDB APIs want:

```scala mdoc
Car.Fields.engine._path
```

If you would rather pass the node directly, one opt-in import — the same line on
Scala 2 and Scala 3 — allows it:

```scala mdoc
import io.moka.syntax._

def exists(field: String): String = field

exists(Car.Fields.engine)
```

On Scala 3 there is a tooling cost: a file that applies this conversion
currently fails SemanticDB extraction, so Metals loses go-to-definition and
find-references for that file. Compilation is unaffected, and `._path` avoids it
entirely.

It is not imported by default on purpose. The conversion only applies where a
`String` is expected, so in a position where the type is *inferred* it would
silently infer the node instead — `Map(Car.Fields.engine -> 1)` would build a map
keyed by the node rather than by `"engine"`. Without the import that is a compile
error.

## Use case: MongoDB update

The whole point — no string literals in queries, and renaming a case class
field (or its bson name) breaks the query at compile time instead of at
runtime:

```scala mdoc
import org.mongodb.scala.bson.collection.immutable.Document

val markRipe = Document(
  "$set" -> Document(Apple.Fields.ripe -> true)
)
```

With the mongo driver's typed builders it reads the same way:

```scala
collection.updateOne(
  Filters.equal(Fruit.Fields.color, "red"),
  Updates.set(Fruit.Fields.weight, 0.3)
)
```
