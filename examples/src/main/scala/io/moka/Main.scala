package io.moka

object Main {

  def main(args: Array[String]): Unit = {
    // Definitions.OneField(1)
    // Definitions.OneField.Fields.a == "a"
    println(Definitions.ManyFields)
    // println(Definitions.NestedClass.Fields.a == "a")
  }
}
