# moka on Scala 2.13

## Setup

```scala
libraryDependencies += "io.github.vimalaguti" %% "moka" % "<version>"
scalacOptions += "-Ymacro-annotations"
```

Import with `import io.moka._` (not `*`).

## Basic

`@moka` generates the companion's `Fields` object (an existing companion is kept).

```scala
import io.moka._
import org.mongodb.scala.model.{Filters, Updates}

@moka
case class Fruit(color: String, weight: Double)

Fruit.Fields.color   // "color" (type "color"); Fruit.Fields.colour does not compile

collection.updateOne(
  Filters.equal(Fruit.Fields.color, "red"),
  Updates.set(Fruit.Fields.weight, 0.3)
)
```

Other object name: `@moka("Params")` → `Fruit.Params`.

## Renamed fields (BSON)

`@BsonProperty` (mongo driver) and `@bsonField` (zio-bson) change the value;
the member keeps the Scala name.

```scala
import org.mongodb.scala.bson.annotations.BsonProperty
import zio.bson.bsonField

@moka
case class Apple(@BsonProperty("c") color: String, @bsonField("w") weight: Double)

Apple.Fields.color   // "c"
Apple.Fields.weight  // "w"
```

## JSON names

`@moka` reads BSON renames only. `@mokaJson` reads zio-json's `@jsonField` into
`JsonFields`; stack it with `@mokaBson` (→ `BsonFields`) for both:

```scala
import zio.json.jsonField

@mokaBson
@mokaJson
case class User(@BsonProperty("b_name") @jsonField("userName") name: String)

User.BsonFields.name  // "b_name"
User.JsonFields.name  // "userName"
```

## Nested fields

A case-class field becomes a node with dotted paths; `_path` is the node's own
path. `Option` and collections are transparent.

**Every nested case class needs its own `@moka`** (the outer copies its names
from it), and must not be declared inside the same object/class as the outer:

```scala
@moka
case class Engine(@BsonProperty("hp") power: Int)

@moka
case class Car(engine: Engine, spare: Option[Engine])

Car.Fields.engine.power   // "engine.hp"
Car.Fields.spare.power    // "spare.hp"
Car.Fields.engine._path   // "engine" — use _path when the node itself is the field
```

Under a `@mokaJson` outer, a nested type with renames needs `@mokaJson` too (a
type without renames serves both). For nested types you can't annotate (generic
or third-party), compile with `-Xmacro-settings:moka.descendUnannotated`: their
annotations are read directly, with a warning, but zio-bson/zio-json renames
compiled in another module are lost.

## Arrays: `_matched` (`$`) and `_all` (`$[]`)

On a collection of case classes:

```scala
@moka
case class Wheel(@BsonProperty("d") diameter: Int)

@moka
case class Bike(wheels: List[Wheel])

Bike.Fields.wheels.diameter           // "wheels.d"
Bike.Fields.wheels._matched.diameter  // "wheels.$.d"
Bike.Fields.wheels._all.diameter      // "wheels.$[].d"

collection.updateOne(
  Filters.equal(Bike.Fields.wheels.diameter, 26),
  Updates.set(Bike.Fields.wheels._matched.diameter, 27)
)
```

## Notes

- Generated members start with `_`; don't name your own fields `_path`, `_matched` or `_all`.
- `import io.moka.syntax._` lets a node be passed where a `String` is expected; `._path` is preferred.
- After changing a rename annotation, run a clean build (`sbt clean compile`) to be safe.
