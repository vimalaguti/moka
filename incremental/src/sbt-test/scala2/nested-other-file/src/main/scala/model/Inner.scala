package model

import io.moka._
import zio.json.jsonField

@mokaJson
final case class Inner(@jsonField("old") a: String)
