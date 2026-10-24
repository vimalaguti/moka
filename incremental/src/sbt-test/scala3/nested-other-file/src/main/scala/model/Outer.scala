package model

import io.moka.*

final case class Outer(inner: Inner)
object Outer {
  val Fields = generateJsonFields[Outer]
}
