package model

import zio.json.jsonField

final case class Inner(@jsonField("old") a: String)
