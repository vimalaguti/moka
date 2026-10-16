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

/** Builds generated roots. Public because the macro expands in user code,
  * outside `io.moka`; the classes behind it stay package-private.
  */
object FieldNames:
  def neutral(children: Map[String, Any]): FieldNames & MokaNames =
    NeutralFieldNames(children)
  def bson(children: Map[String, Any]): FieldNames & MokaBsonNames =
    BsonFieldNames(children)
  def json(children: Map[String, Any]): FieldNames & MokaJsonNames =
    JsonFieldNames(children)

/* One root class per marker, so the runtime instance really implements the
 * marker its static type promises (spec rule 1); a cast alone would not. */
private[moka] final class NeutralFieldNames(children: Map[String, Any])
    extends FieldNames(children),
      MokaNames
private[moka] final class BsonFieldNames(children: Map[String, Any])
    extends FieldNames(children),
      MokaBsonNames
private[moka] final class JsonFieldNames(children: Map[String, Any])
    extends FieldNames(children),
      MokaJsonNames

/** The Scala 3 counterpart of a generated nested object. */
class PathNode[P <: String](val _path: P, children: Map[String, Any])
    extends FieldNames(children),
      FieldPath[P]:
  override def toString: String = _path
