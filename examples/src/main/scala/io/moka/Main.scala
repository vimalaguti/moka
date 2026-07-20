package io.moka

object Main {

  def main(args: Array[String]): Unit = {
    import Definitions.ManyFields
    println(
      s"ManyFields field names: ${ManyFields.Fields.a}, ${ManyFields.Fields.b}"
    )
    println(
      s"DiffFields bson names: ${Definitions.DiffFields.Fields.a}, ${Definitions.DiffFields.Fields.b}"
    )
  }
}
