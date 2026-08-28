package io.moka

/** A generated field that names a sub-document rather than a scalar.
  *
  * `P` is the singleton type of the dotted path this node addresses, so
  * `path` keeps a literal type just like a leaf field's value does.
  */
class FieldPath[P <: String](val path: P) {
  override def toString: String = path
}
