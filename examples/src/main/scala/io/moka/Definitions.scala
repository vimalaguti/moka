package io.moka

import org.mongodb.scala.bson.annotations.BsonProperty
import zio.bson.bsonField

object Definitions {
  @moka
  final case class NoFields()
  object NoFields {
    val Fields = generateFields[NoFields]
  }
  @moka
  final case class OneField(a: Int)
  object OneField {
    val Fields = generateFields[OneField]
  }
  @moka
  final case class ManyFields(a: Int, b: Int)
  object ManyFields {
    def apply(): ManyFields = ManyFields(1, 2)
    val Fields              = generateFields[ManyFields]
  }

  @moka
  final case class DiffFields(@BsonProperty("abc") a: Int, b: String)
  object DiffFields {
    val Fields = generateFields[DiffFields]
  }
  case class A(value: Int) extends AnyVal
  @moka
  final case class NestedSimple(a: A)
  object NestedSimple {
    val Fields = generateFields[NestedSimple]
  }
  @moka
  final case class NestedClass(a: OneField)
  object NestedClass {
    val Fields = generateFields[NestedClass]
  }
  @moka
  case class WithCompanionObject(a: Int)
  object WithCompanionObject {
    val b                                         = 3
    def someFunction(a: Int): WithCompanionObject = WithCompanionObject(a)
    val Fields = generateFields[WithCompanionObject]
  }
  @moka("Renamed")
  case class RenamedObject(a: Int)
  object RenamedObject {
    val Renamed = generateFields[RenamedObject]
  }
  @moka
  final case class BsonAnnotatedClass(@BsonProperty("b") a: Int)
  object BsonAnnotatedClass {
    val Fields = generateFields[BsonAnnotatedClass]
  }
  @moka
  final case class ZioBsonAnnotatedClass(@bsonField("b") a: Int)
  object ZioBsonAnnotatedClass {
    val Fields = generateFields[ZioBsonAnnotatedClass]
  }

  @moka
  final case class BsonAnnotatedClassCO(@BsonProperty("b") a: Int)
  object BsonAnnotatedClassCO {
    val a      = 0
    val Fields = generateFields[BsonAnnotatedClassCO]
  }
  @moka
  final case class ZioBsonAnnotatedClassCO(@bsonField("b") a: Int)
  object ZioBsonAnnotatedClassCO {
    val a      = 0
    val Fields = generateFields[ZioBsonAnnotatedClassCO]
  }
}
