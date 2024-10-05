package io.moka

object Main {
  
    def main(args: Array[String]): Unit = {
        // Definitions.OneField(1)
        // Definitions.OneField.Fields.a == "a"
        Definitions.ManyFields(1, 2)
        Definitions.ManyFields.Fields.b == "b"

    }
}
