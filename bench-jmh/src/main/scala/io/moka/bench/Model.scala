package io.moka.bench

import io.moka._

/** Fixed model for the runtime benchmark. Nested types are at package level
  * because the Scala 2 macro cannot resolve siblings of the annotated class.
  */
final case class Deep(d: Int)

final case class Mid(m: Int, deep: Deep)

@moka
final case class Root(leaf: String, mid: Mid)
object Root {
  val Fields = generateFields[Root]
}
