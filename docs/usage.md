# Usage

To use it, just annotate your case class with `@moka`: 
```scala mdoc
import io.moka.Moka.moka

@moka
case class Simple(color: String)

Simple.Fields.color == "color"
```
At compile time, the `Fields` object is created inside the companion object.

Look at the tests to check all the supported use cases.

## Companion object
Defining a companion object is supported and should not interfere
```scala mdoc
@moka
case class WithCompanion(a: Int)
object WithCompanion

WithCompanion.Fields.a == "a"
```

## Override the default name
The default name for the generated object is `Fields`. 
If you want to change it, pass the name to the annotation:

```scala mdoc
@moka("Params")
final case class RenamedField(a: Int)
RenamedField.Params.a == "a"
```

## Bson
As the main focus is to support mongoDB queries, it's common to rename a field in the bson representation.
For the annotated parameter, this macro creates a `val` as the field name, but with the annotated name as value:

### For standard mongodb
```scala mdoc
import org.mongodb.scala.bson.annotations.BsonProperty
@moka
case class MongodbJava(@BsonProperty("c") color: Int)
MongodbJava.Fields.color == "c"
```

### For zio-bson
```scala mdoc
import zio.bson.bsonField
@moka
case class ZioBsonSupport(@bsonField("c") color: Int)
ZioBsonSupport.Fields.color == "c"
```
