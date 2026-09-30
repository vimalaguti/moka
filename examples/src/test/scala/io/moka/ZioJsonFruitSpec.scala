package io.moka

import zio.json._

// Package-level definitions so Scala 2 macro annotation can inspect types
final case class FruitId(value: String)  extends AnyVal
final case class Variety(value: String)  extends AnyVal
final case class Origin(value: String)   extends AnyVal
final case class Producer(value: String) extends AnyVal

@moka
final case class Fruit(
    _id: FruitId,
    @jsonField("v") variety: Variety,
    @jsonField("o") origin: Origin,
    @jsonField("p") producer: Producer
)
object Fruit {
  val Fields = generateFields[Fruit]
}

final case class Nutrition(@jsonField("cal") calories: Int)

@moka
final case class FruitBasket(
    @jsonField("f") item: Fruit,
    @jsonField("nut") nutrition: Nutrition
)
object FruitBasket {
  val Fields = generateFields[FruitBasket]
}

class ZioJsonFruitSpec extends munit.FunSuite {

  test("zio-json @jsonField renames fields on Fruit") {
    assertEquals(Fruit.Fields._id, "_id")
    assertEquals(Fruit.Fields.variety, "v")
    assertEquals(Fruit.Fields.origin, "o")
    assertEquals(Fruit.Fields.producer, "p")
  }

  test("zio-json @jsonField supports nested case classes") {
    assertEquals(FruitBasket.Fields.item._path, "f")
    assertEquals(FruitBasket.Fields.item.variety, "f.v")
    assertEquals(FruitBasket.Fields.nutrition._path, "nut")
    assertEquals(FruitBasket.Fields.nutrition.calories, "nut.cal")
  }
}
