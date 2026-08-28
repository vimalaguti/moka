package io.moka

/** Root of a generated `Fields` object. Carries no path of its own, so a
  * top-level field may still be named `path`.
  *
  * A Scala 3 expression macro cannot introduce definitions — an object returned
  * from an expression has its type widened to `Object` at the binding site and
  * loses every member — so members are exposed through a structural refinement,
  * which routes selection through `selectDynamic`.
  */
class FieldNames(children: Map[String, Any]) extends Selectable:
  def selectDynamic(name: String): Any = children(name)

/** The Scala 3 counterpart of a generated nested object. */
class PathNode[P <: String](val path: P, children: Map[String, Any])
    extends FieldNames(children),
      FieldPath[P]:
  override def toString: String = path
