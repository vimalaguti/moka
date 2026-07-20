---
sidebar_position: 5
---

# Features

Everything on this page works identically on Scala 2.13 and Scala 3; the
snippets use the [cross-compiled style](cross.md).

## What it does

For every field of a case class, the generated `Fields` object exposes a
member with the same name whose **value** is the field's name (or its bson
name, see below). Misspelled names fail at compile time.

```scala mdoc
import io.moka.Moka
import io.moka.Moka.moka

@moka
case class Apple(color: String, ripe: Boolean)
object Apple {
  val Fields = Moka.generateFields[Apple]
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

The generated object can have any name: name the val as you like (and pass
the same name to the annotation for Scala 2):

```scala mdoc
@moka("Params")
case class Renamed(a: Int)
object Renamed {
  val Params = Moka.generateFields[Renamed]
}

Renamed.Params.a
```

## Bson annotations

When a field is renamed in its bson representation, the `Fields` member
keeps the Scala name but carries the **bson name as value** — so queries use
the name that is actually in the database. Both the official mongo driver
annotation and zio-bson are supported:

```scala mdoc
import org.mongodb.scala.bson.annotations.BsonProperty
import zio.bson.bsonField

@moka
case class Fruit(@BsonProperty("c") color: String, @bsonField("w") weight: Double)
object Fruit {
  val Fields = Moka.generateFields[Fruit]
}

Fruit.Fields.color
Fruit.Fields.weight
```

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
