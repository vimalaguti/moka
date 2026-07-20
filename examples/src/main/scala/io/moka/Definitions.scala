package io.moka

import Moka.moka
import org.mongodb.scala.bson.annotations.BsonProperty
import zio.bson.bsonField

object Definitions {
  @moka
  final case class NoFields()
  object NoFields {
    val Fields = Moka.generateFields[NoFields]
  }
  @moka
  final case class OneField(a: Int)
  object OneField {
    val Fields = Moka.generateFields[OneField]
  }
  @moka
  final case class ManyFields(a: Int, b: Int)
  object ManyFields {
    def apply(): ManyFields = ManyFields(1, 2)
    val Fields              = Moka.generateFields[ManyFields]
  }

  @moka
  final case class DiffFields(@BsonProperty("abc") a: Int, b: String)
  object DiffFields {
    val Fields = Moka.generateFields[DiffFields]
  }
  case class A(value: Int) extends AnyVal
  @moka
  final case class NestedSimple(a: A)
  object NestedSimple {
    val Fields = Moka.generateFields[NestedSimple]
  }
  @moka
  final case class NestedClass(a: OneField)
  object NestedClass {
    val Fields = Moka.generateFields[NestedClass]
  }
  @moka
  case class WithCompanionObject(a: Int)
  object WithCompanionObject {
    val b                                         = 3
    def someFunction(a: Int): WithCompanionObject = WithCompanionObject(a)
    val Fields = Moka.generateFields[WithCompanionObject]
  }
  @moka("Renamed")
  case class RenamedObject(a: Int)
  object RenamedObject {
    val Renamed = Moka.generateFields[RenamedObject]
  }
  @moka
  final case class BsonAnnotatedClass(@BsonProperty("b") a: Int)
  object BsonAnnotatedClass {
    val Fields = Moka.generateFields[BsonAnnotatedClass]
  }
  @moka
  final case class ZioBsonAnnotatedClass(@bsonField("b") a: Int)
  object ZioBsonAnnotatedClass {
    val Fields = Moka.generateFields[ZioBsonAnnotatedClass]
  }

  @moka
  final case class BsonAnnotatedClassCO(@BsonProperty("b") a: Int)
  object BsonAnnotatedClassCO {
    val a      = 0
    val Fields = Moka.generateFields[BsonAnnotatedClassCO]
  }
  @moka
  final case class ZioBsonAnnotatedClassCO(@bsonField("b") a: Int)
  object ZioBsonAnnotatedClassCO {
    val a      = 0
    val Fields = Moka.generateFields[ZioBsonAnnotatedClassCO]
  }
}
