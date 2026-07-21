package io.moka

import org.mongodb.scala.bson.annotations.BsonProperty
import zio.bson.bsonField

/** Scala 2-only style: @moka generates the companion object (or rewrites an
  * existing one), so no placeholder val is needed. This is the cleanest way
  * to use moka on Scala 2, but it does not cross-compile: Scala 3 requires
  * the explicit companion with `val Fields = generateFields[T]`.
  */
object Scala2Definitions {
  @moka
  final case class NoCompanion(a: Int)

  @moka("Renamed")
  final case class RenamedNoCompanion(a: Int)

  @moka
  final case class ManyFieldsNoCompanion(a: Int, b: Int)

  @moka
  final case class BsonNoCompanion(@BsonProperty("renamed") a: Int)

  @moka
  final case class ZioBsonNoCompanion(@bsonField("renamed") a: Int)

  @moka
  final case class WithMembers(a: Int)
  object WithMembers {
    val default: WithMembers = WithMembers(0)
  }

  @moka("Renamed")
  final case class RenamedWithCompanion(a: Int)
  object RenamedWithCompanion {
    val default: RenamedWithCompanion = RenamedWithCompanion(1)
  }
}
