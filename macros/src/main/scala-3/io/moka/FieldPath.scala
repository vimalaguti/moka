package io.moka

/** Root of a generated `Fields` object. Carries no path of its own, so a
  * top-level field may still be named `path`.
  */
class FieldNames(children: Map[String, Any]) extends Selectable:
  def selectDynamic(name: String): Any = children(name)

/** A generated field that names a sub-document rather than a scalar.
  *
  * `P` is the singleton type of the dotted path this node addresses, so `path`
  * keeps a literal type just like a leaf field's value does.
  */
class FieldPath[P <: String](val path: P, children: Map[String, Any])
    extends FieldNames(children):
  override def toString: String = path
