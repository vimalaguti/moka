package io.moka

import Moka.moka
import org.mongodb.scala.bson.annotations.BsonProperty

object Definitions {

  final case class NoFields()
  // @moka
  final case class OneField(a: Int)
  // @moka
  final case class ManyFields(a: Int, b: Int)
  object ManyFields {
    def apply(): ManyFields = ManyFields(1, 2)
  }

  final case class DiffFields(a: Int, b: String)
  // final case class NestedClass(one: OneField)

  @moka
  final case class NestedClass(a: OneField)

}
