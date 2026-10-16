package io.moka.bench

import io.moka._

/** Fixed model for the runtime benchmark. Nested types are at package level
  * because the Scala 2 macro cannot resolve siblings of the annotated class,
  * and carry their own moka object because on Scala 2.13 an outer type takes a
  * nested type's names from it.
  */
@moka
final case class Deep(d: Int)
object Deep {
  val Fields = generateFields[Deep]
}

@moka
final case class Mid(m: Int, deep: Deep)
object Mid {
  val Fields = generateFields[Mid]
}

@moka
final case class Root(leaf: String, mid: Mid)
object Root {
  val Fields = generateFields[Root]
}
