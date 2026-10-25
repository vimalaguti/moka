package io.moka

import org.mongodb.scala.bson.annotations.BsonProperty
import zio.bson.bsonField

/** Models exercising nested path generation.
  *
  * They live at package level, not inside an object, because the Scala 2
  * annotation macro runs before the typer and cannot resolve a type declared as
  * a member of the same enclosing object as the annotated case class.
  *
  * Every descendable nested type carries its own `@moka` and placeholder: on
  * Scala 2.13 an outer type takes a nested type's names from that type's own
  * generated object (`specs/specs/2026-10-16-nested-composition-design.md`).
  */
@moka
final case class Level3(@BsonProperty("z") deep: Int)
object Level3 {
  val Fields = generateFields[Level3]
}

@moka
final case class Level2(c: Int, three: Level3)
object Level2 {
  val Fields = generateFields[Level2]
}

@moka
final case class Level1(@bsonField("r") renamed: Level2, plain: String)
object Level1 {
  val Fields = generateFields[Level1]
}

/** Directly self-referential: descent must terminate. */
@moka
final case class SelfRef(value: Int, child: SelfRef)
object SelfRef {
  val Fields = generateFields[SelfRef]
}

/** Option and collections are transparent: MongoDB's dot notation does not
  * distinguish an optional sub-document, an array of them, or a plain one.
  */
@moka
final case class Wrapped(
    maybe: Option[Level2],
    many: List[Level3],
    nestedOpt: Option[List[Level3]],
    keyed: Map[String, Level3]
)
object Wrapped {
  val Fields = generateFields[Wrapped]
}

/** Collection fields additionally expose MongoDB's array operators. */
@moka
final case class BasketItem(@BsonProperty("q") qty: Int, note: String)
object BasketItem {
  val Fields = generateFields[BasketItem]
}

@moka
final case class Basket(
    items: List[BasketItem],
    maybe: Option[BasketItem],
    tags: List[String]
)
object Basket {
  val Fields = generateFields[Basket]
}

/** A cycle reached through a collection rather than directly. The two versions
  * catch this by different mechanisms, so parity here is worth pinning.
  */
@moka
final case class TreeNode(name: String, kids: List[TreeNode])
object TreeNode {
  val Fields = generateFields[TreeNode]
}

/** Collection kinds other than List. */
@moka
final case class Bag(
    vec: Vector[BasketItem],
    set: Set[BasketItem],
    seq: Seq[BasketItem]
)
object Bag {
  val Fields = generateFields[Bag]
}

/** An array inside an array: operators must be available at both levels. */
@moka
final case class Row(cells: List[BasketItem])
object Row {
  val Fields = generateFields[Row]
}

@moka
final case class Grid(rows: List[Row])
object Grid {
  val Fields = generateFields[Grid]
}

/** Maps with one type argument are still maps, not collections: descent stops
  * at them as at `Map`.
  */
@moka
final case class IntKeyed(
    ints: scala.collection.immutable.IntMap[Level3],
    longs: scala.collection.immutable.LongMap[Level3],
    mutableLongs: scala.collection.mutable.LongMap[Level3]
)
object IntKeyed {
  val Fields = generateFields[IntKeyed]
}
