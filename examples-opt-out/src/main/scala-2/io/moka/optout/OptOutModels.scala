package io.moka.optout

import io.moka._
import org.mongodb.scala.bson.annotations.BsonProperty
import zio.bson.bsonField

/** Nested types compiled in this project's main run, so the outer types in its
  * test run see them only through classfiles. None of them needs a moka object:
  * the project compiles with `-Xmacro-settings:moka.descendUnannotated`.
  */

/** No moka object; mongo-scala-bson's `@BsonProperty` survives in the
  * classfile, so reading it directly works.
  */
final case class PlainInner(@BsonProperty("p") a: Int, b: String)

/** No moka object; zio-bson's `@bsonField` is a plain `Annotation` that scalac
  * 2.13 drops from the classfile. Reading it directly cannot see the rename:
  * the loss the opt-out's warning is about.
  */
final case class LostInner(@bsonField("lost") a: Int)

/** Has a moka object: the opt-out does not stop composition. */
@moka
final case class ObjectInner(@bsonField("zr") a: String)
object ObjectInner {
  val Fields = generateFields[ObjectInner]
}

/** Generic, so it can never carry a moka object. */
final case class Box[T](@BsonProperty("v") value: T)

/** No renames at all. */
final case class Unrenamed(a: Int)

/** Read directly, but its own nested type has an object to compose. */
final case class DirectWithComposed(@BsonProperty("o") obj: ObjectInner)
