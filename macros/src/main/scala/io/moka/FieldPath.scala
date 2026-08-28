package io.moka

/** A generated field that names a sub-document rather than a scalar.
  *
  * `P` is the singleton type of the dotted path the node addresses, so `path`
  * keeps a literal type just like a leaf field's value does.
  *
  * On Scala 2 the macro generates plain objects extending this trait, which the
  * compiler folds to constants at every call site. Scala 3 cannot generate
  * objects from an expression macro, so there it is the supertype of the
  * concrete [[PathNode]] the macro instantiates.
  */
trait FieldPath[P <: String] {
  def path: P
}
