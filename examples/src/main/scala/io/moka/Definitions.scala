package io.moka

import Moka.moka
import org.mongodb.scala.bson.annotations.BsonProperty

object Definitions:

  final case class NoFields()
  @moka
  final case class OneField(a: Int)
  @moka
  final case class ManyFields(a: Int, b: Int)
  case object ManyFields {
    Moka.generateFields[ManyFields]
    def apply(): ManyFields = ManyFields(1, 2)
  }

  final case class DiffFields(@BsonProperty("abc") a: Int, b: String)
  
  @moka
  final case class NestedClass(a: OneField)
