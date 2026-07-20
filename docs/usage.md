# Usage

Moka offers three styles depending on which Scala versions you target.

## Cross-compiled sources (Scala 2.13 + Scala 3)

Annotate the case class with `@moka` **and** declare the placeholder val in
an explicit companion object. The same source compiles on both versions:
on Scala 2 the annotation rewrites the val into a generated object, on
Scala 3 the annotation is a no-op and `generateFields` expands inline.

```scala mdoc
import io.moka.Moka
import io.moka.Moka.moka

@moka
case class Simple(color: String)
object Simple {
  val Fields = Moka.generateFields[Simple]
}

Simple.Fields.color == "color"
```

Existing companion members are preserved:

```scala mdoc
@moka
case class WithCompanion(a: Int)
object WithCompanion {
  val default: WithCompanion = WithCompanion(0)
  val Fields = Moka.generateFields[WithCompanion]
}

WithCompanion.Fields.a == "a"
```

Misspelled field names do not compile — that is the point:

```scala mdoc:fail
Simple.Fields.colour
```

## Scala 2 only

On Scala 2 the annotation can generate the companion object by itself:

```scala
@moka
case class Simple2(color: String)

Simple2.Fields.color == "color"

// custom object name
@moka("Params")
final case class Renamed2(a: Int)
Renamed2.Params.a == "a"
```

## Scala 3 only

On Scala 3 the annotation is not needed at all:

```scala
case class Simple3(color: String)
object Simple3 {
  val Fields = Moka.generateFields[Simple3]
}

// the object name is simply the val's name
case class Renamed3(a: Int)
object Renamed3 {
  val Params = Moka.generateFields[Renamed3]
}
```

## Override the default name

In the cross-compiled style, name the val as you like — the Scala 2
annotation argument must match it:

```scala mdoc
@moka("Params")
final case class RenamedField(a: Int)
object RenamedField {
  val Params = Moka.generateFields[RenamedField]
}

RenamedField.Params.a == "a"
```

## Bson

As the main focus is to support mongoDB queries, it's common to rename a
field in the bson representation. For the annotated parameter, the field
keeps its name but carries the annotated name as value:

### For standard mongodb
```scala mdoc
import org.mongodb.scala.bson.annotations.BsonProperty

@moka
case class MongodbJava(@BsonProperty("c") color: Int)
object MongodbJava {
  val Fields = Moka.generateFields[MongodbJava]
}

MongodbJava.Fields.color == "c"
```

### For zio-bson
```scala mdoc
import zio.bson.bsonField

@moka
case class ZioBsonSupport(@bsonField("c") color: Int)
object ZioBsonSupport {
  val Fields = Moka.generateFields[ZioBsonSupport]
}

ZioBsonSupport.Fields.color == "c"
```
