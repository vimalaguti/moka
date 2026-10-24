package model

import zio.json.jsonField

final case class Invoice(@jsonField("old") number: String)
