package io.moka

/** Scala 3-only style: generateFields without the @moka annotation.
  * This does not cross-compile (the Scala 2 macro only rewrites the
  * placeholder val when the class is annotated with @moka).
  */
object Scala3Definitions {
  final case class PlainFields(a: Int, b: Int)
  object PlainFields {
    val Fields = Moka.generateFields[PlainFields]
  }
}
