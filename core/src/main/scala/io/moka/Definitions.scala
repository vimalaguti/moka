package io.moka

import Moka.moka

object Definitions {

  final case class NoFields()
  // @moka
  final case class OneField(a: Int)
  @moka
  final case class ManyFields(a: Int, b: Int)
  final case class DiffFields(a: Int, b: String)
  // final case class NestedClass(one: OneField)

}
