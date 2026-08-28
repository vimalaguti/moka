package io.moka

import org.mongodb.scala.bson.annotations.BsonProperty
import zio.bson.bsonField

/** Models exercising nested path generation.
  *
  * They live at package level, not inside an object, because the Scala 2
  * annotation macro runs before the typer and cannot resolve a type declared as
  * a member of the same enclosing object as the annotated case class. Note that
  * only the outermost class needs `@moka`.
  */
final case class Level3(@BsonProperty("z") deep: Int)

final case class Level2(c: Int, three: Level3)

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
final case class BasketItem(@BsonProperty("q") qty: Int, note: String)

@moka
final case class Basket(
    items: List[BasketItem],
    maybe: Option[BasketItem],
    tags: List[String]
)
object Basket {
  val Fields = generateFields[Basket]
}
