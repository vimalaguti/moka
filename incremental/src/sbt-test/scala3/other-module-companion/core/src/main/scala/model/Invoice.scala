package model

import io.moka.*
import zio.json.jsonField

final case class Invoice(@jsonField("old") number: String)
object Invoice {
  val JsonFields = generateJsonFields[Invoice]
}
