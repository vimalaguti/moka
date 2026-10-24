package use

import io.moka.*
import model.Invoice

object Repository {
  val InvoiceFields = generateJsonFields[Invoice]
}
