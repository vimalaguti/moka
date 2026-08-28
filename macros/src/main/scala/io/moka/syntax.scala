package io.moka

import scala.language.implicitConversions

/** Opt-in conversion letting a path node stand in for its own `String` path
  * where one is expected, e.g. `Filters.exists(Fields.a)`.
  *
  * It is deliberately not in [[FieldPath]]'s companion: Scala 3 reports a
  * feature warning at every site where a conversion is applied, so an
  * always-in-scope conversion would make the default experience differ between
  * the two Scala versions. `.path` works everywhere without this import.
  */
object syntax {
  implicit def fieldPathToString[P <: String](node: FieldPath[P]): P = node.path
}
