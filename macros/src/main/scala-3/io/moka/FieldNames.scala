package io.moka

/** Root of a generated `Fields` object. Carries no path of its own, so a
  * top-level field may still be named `_path`.
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
  def neutral(tree: String*): FieldNames & MokaNames =
    NeutralFieldNames(decode(tree))
  def bson(tree: String*): FieldNames & MokaBsonNames =
    BsonFieldNames(decode(tree))
  def json(tree: String*): FieldNames & MokaJsonNames =
    JsonFieldNames(decode(tree))

  /** Separates a member's name, its path segment and its kind. A NUL cannot
    * occur in a Scala identifier or a BSON/JSON field name.
    */
  private[moka] inline val Sep = '\u0000'

  /** Rebuilds the root's children from the macro's encoding of the tree.
    *
    * The macro emits the tree as string constants rather than code building it:
    * one `Tuple2` per member made the root initializer outgrow the JVM's 64 KB
    * method limit on large models, and compiling it dominated build time. A
    * node is `<member count>;` followed by its members, each
    * `name NUL segment NUL` and then `L` (a leaf) or `N<node>`. The encoding is
    * split into chunks only because a JVM string constant is capped at 64 KB.
    */
  private def decode(chunks: Seq[String]): Map[String, Any] =
    val enc = chunks.mkString
    var i   = 0
    def node(prefix: String): Map[String, Any] =
      val semi  = enc.indexOf(';', i)
      val count = enc.substring(i, semi).toInt
      i = semi + 1
      val b = Map.newBuilder[String, Any]
      for _ <- 0 until count do
        val nameEnd = enc.indexOf(Sep, i)
        val segEnd  = enc.indexOf(Sep, nameEnd + 1)
        val name    = enc.substring(i, nameEnd)
        val seg     = enc.substring(nameEnd + 1, segEnd)
        val path    = if prefix.isEmpty then seg else prefix + "." + seg
        val kind    = enc.charAt(segEnd + 1)
        i = segEnd + 2
        b += name -> (if kind == 'L' then path else PathNode(path, node(path)))
      b.result()
    node("")

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
      FieldPath[P]
