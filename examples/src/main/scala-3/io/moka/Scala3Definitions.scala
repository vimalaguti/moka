package io.moka

import org.mongodb.scala.bson.annotations.BsonProperty
import zio.bson.bsonField

/** Scala 3-only style: `generateFields[T]` in the companion, no @moka
  * annotation. This is the cleanest way to use moka on Scala 3, but it does not
  * cross-compile: the Scala 2 macro only rewrites the placeholder val when the
  * case class is annotated with @moka.
  */
object Scala3Definitions {
  final case class PlainFields(a: Int, b: Int)
  object PlainFields {
    val Fields = generateFields[PlainFields]
  }

  final case class BsonFields(
      @BsonProperty("renamed") a: Int,
      @bsonField("znamed") b: Int
  )
  object BsonFields {
    val Fields = generateFields[BsonFields]
  }

  // the generated object's name is simply the val's name
  final case class CustomName(a: Int)
  object CustomName {
    val Renamed = generateFields[CustomName]
  }

  final case class WithMembers(a: Int)
  object WithMembers {
    val default: WithMembers = WithMembers(0)
    val Fields               = generateFields[WithMembers]
  }

  final case class Inner(x: Int)
  final case class Nested(inner: Inner)
  object Nested {
    val Fields = generateFields[Nested]
  }
}
