package io.moka

import org.mongodb.scala.bson.annotations.BsonProperty
import zio.bson.bsonField
import zio.json.jsonField

/** Nested types for the composition contract
  * (`specs/specs/2026-10-16-nested-composition-design.md`).
  *
  * They are compiled in the `examples` main run, so outer types declared in the
  * test run see them only through their classfiles — where scalac 2.13 has
  * dropped zio-bson's `@bsonField` and zio-json's `@jsonField`. On 2.13 the
  * names must therefore come from each type's own generated object.
  */

/** JSON counterpart of [[SharedZioBsonInner]]. */
@mokaJson
final case class SharedZioJsonInner(@jsonField("jr") a: String)
object SharedZioJsonInner {
  val JsonFields = generateJsonFields[SharedZioJsonInner]
}

/** The object is found by its marker, not by a fixed name. */
@moka
final case class SharedCustomNamedInner(@bsonField("cr") a: String)
object SharedCustomNamedInner {
  val Custom = generateFields[SharedCustomNamedInner]
}

/** No renames anywhere: its object is neutral, valid for every format. */
@moka
final case class SharedNeutralInner(a: String)
object SharedNeutralInner {
  val Fields = generateFields[SharedNeutralInner]
}

/** Renamed to its own name: still "renamed", so not neutral (spec rule 1). */
@moka
final case class SharedSelfRenamed(@bsonField("a") a: String)
object SharedSelfRenamed {
  val Fields = generateFields[SharedSelfRenamed]
}

/** Element type of collections over a composed type. */
@moka
final case class ComposedItem(@bsonField("q") qty: Int, note: String)
object ComposedItem {
  val Fields = generateFields[ComposedItem]
}

/** A composed type that itself composes [[ComposedItem]], through renamed
  * fields.
  */
@moka
final case class ComposedMid(
    @bsonField("it") item: ComposedItem,
    @bsonField("its") items: List[ComposedItem]
)
object ComposedMid {
  val Fields = generateFields[ComposedMid]
}

/** Two objects of the same format with identical members: not ambiguous. */
@moka
final case class SharedDuplicateObjects(@bsonField("d") a: String)
object SharedDuplicateObjects {
  val Fields     = generateFields[SharedDuplicateObjects]
  val BsonFields = generateBsonFields[SharedDuplicateObjects]
}

/** Empty renames are absent (spec rule 5), for every annotation. */
@moka
final case class SharedEmptyRenames(
    @bsonField("") a: String,
    @BsonProperty("") b: String,
    @jsonField("") c: String
)
object SharedEmptyRenames {
  val Fields     = generateFields[SharedEmptyRenames]
  val JsonFields = generateJsonFields[SharedEmptyRenames]
}
