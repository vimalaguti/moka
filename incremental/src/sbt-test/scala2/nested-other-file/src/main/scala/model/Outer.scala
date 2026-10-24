package model

import io.moka._

@mokaJson
final case class Outer(inner: Inner)
