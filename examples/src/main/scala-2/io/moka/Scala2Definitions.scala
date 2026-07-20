package io.moka

import Moka.moka

/** Scala 2-only style: @moka can generate the companion object itself,
  * so no placeholder val is needed. This does not cross-compile
  * (Scala 3 requires the explicit companion with generateFields).
  */
object Scala2Definitions {
  @moka
  final case class NoCompanion(a: Int)

  @moka("Renamed")
  final case class RenamedNoCompanion(a: Int)
}
