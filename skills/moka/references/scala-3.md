# moka on Scala 3

## Setup

```scala
libraryDependencies += "io.github.vimalaguti" %% "moka" % "<version>"
```

No compiler flags. Scala 3.3 LTS or later.

## Basic

```scala
import io.moka.*
import org.mongodb.scala.model.{Filters, Updates}

case class Fruit(color: String, weight: Double)
object Fruit {
  val Fields = generateFields[Fruit]   // the val name is the object name
}

Fruit.Fields.color                      // "color" (type "color"); Fruit.Fields.colour does not compile

collection.updateOne(
  Filters.equal(Fruit.Fields.color, "red"),
  Updates.set(Fruit.Fields.weight, 0.3)
)
```

Don't ascribe the val (`val Fields: FieldNames = ...` loses every member).

## Renamed fields (BSON)

`@BsonProperty` (mongo driver) and `@bsonField` (zio-bson) change the value;
the member keeps the Scala name.

```scala
import org.mongodb.scala.bson.annotations.BsonProperty
import zio.bson.bsonField

case class Apple(@BsonProperty("c") color: String, @bsonField("w") weight: Double)
object Apple {
  val Fields = generateFields[Apple]
}

Apple.Fields.color   // "c"
Apple.Fields.weight  // "w"
```

## JSON names

`generateFields` reads BSON renames only. For zio-json's `@jsonField` use
`generateJsonFields` (and `generateBsonFields` is an explicit BSON alias):

```scala
import zio.json.jsonField

case class User(@BsonProperty("b_name") @jsonField("userName") name: String)
object User {
  val Fields     = generateFields[User]      // Fields.name     == "b_name"
  val JsonFields = generateJsonFields[User]  // JsonFields.name == "userName"
}
```

## Nested fields

A case-class field becomes a node with dotted paths; `_path` is the node's own
path. `Option` and collections are transparent. The nested type needs nothing.

```scala
case class Engine(@BsonProperty("hp") power: Int)
case class Car(engine: Engine, spare: Option[Engine])
object Car {
  val Fields = generateFields[Car]
}

Car.Fields.engine.power   // "engine.hp"
Car.Fields.spare.power    // "spare.hp"
Car.Fields.engine._path   // "engine" — use _path when the node itself is the field
```

## Arrays: `_matched` (`$`) and `_all` (`$[]`)

On a collection of case classes:

```scala
case class Wheel(@BsonProperty("d") diameter: Int)
case class Bike(wheels: List[Wheel])
object Bike {
  val Fields = generateFields[Bike]
}

Bike.Fields.wheels.diameter           // "wheels.d"
Bike.Fields.wheels._matched.diameter  // "wheels.$.d"
Bike.Fields.wheels._all.diameter      // "wheels.$[].d"

collection.updateOne(
  Filters.equal(Bike.Fields.wheels.diameter, 26),
  Updates.set(Bike.Fields.wheels._matched.diameter, 27)
)
```

Giving `Wheel` its own `val Fields = generateFields[Wheel]` is still useful
where names relative to the element are needed (`Wheel.Fields.diameter == "d"`).

## Notes

- Generated members start with `_`; don't name your own fields `_path`, `_matched` or `_all`.
- `import io.moka.syntax._` lets a node be passed where a `String` is expected; `._path` is preferred.
- **After changing a rename annotation, always do a clean build** (`sbt clean compile`).
  Incremental compilation does not see the change outside the renamed type's own file:
  outer types and `generate…Fields` calls elsewhere keep the old name, which still compiles
  and silently matches nothing in MongoDB.
- Prefer `val Fields = generateFields[T]` in `T`'s own companion over calls in other files
  or modules: it is recompiled with `T`.
