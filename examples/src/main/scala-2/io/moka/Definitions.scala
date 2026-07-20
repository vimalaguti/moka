package io.moka

import Moka.moka
import org.mongodb.scala.bson.annotations.BsonProperty
import zio.bson.bsonField

object Definitions {
  @moka
  final case class NoFields()
  @moka
  final case class OneField(a: Int)
  @moka
  final case class ManyFields(a: Int, b: Int)
  object ManyFields {
    def apply(): ManyFields = ManyFields(1, 2)
  }

  @moka
  final case class DiffFields(@BsonProperty("abc") a: Int, b: String)
  case class A(value: Int) extends AnyVal
  @moka
  final case class NestedSimple(a: A)
  @moka
  final case class NestedClass(a: OneField)
  @moka
  case class WithCompanionObject(a: Int)
  object WithCompanionObject {
    val b = 3
    def someFunction(a: Int): WithCompanionObject = WithCompanionObject(a)
  }
  @moka("Renamed")
  case class RenamedObject(a: Int)
  @moka
  final case class BsonAnnotatedClass(@BsonProperty("b") a: Int)
  @moka
  final case class ZioBsonAnnotatedClass(@bsonField("b") a: Int)

  @moka
  final case class BsonAnnotatedClassCO(@BsonProperty("b") a: Int)
  object BsonAnnotatedClassCO {
    val a = 0
  }
  @moka
  final case class ZioBsonAnnotatedClassCO(@bsonField("b") a: Int)
  object ZioBsonAnnotatedClassCO {
    val a = 0
  }
}
