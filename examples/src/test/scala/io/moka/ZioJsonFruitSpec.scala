package io.moka

import zio.json._

// Package-level definitions so Scala 2 macro annotation can inspect types
final case class FruitId(value: String)  extends AnyVal
final case class Variety(value: String)  extends AnyVal
final case class Origin(value: String)   extends AnyVal
final case class Producer(value: String) extends AnyVal

@mokaJson
final case class Fruit(
    _id: FruitId,
    @jsonField("v") variety: Variety,
    @jsonField("o") origin: Origin,
    @jsonField("p") producer: Producer
)
object Fruit {
  val JsonFields = generateJsonFields[Fruit]
}

final case class Nutrition(@jsonField("cal") calories: Int)

@mokaJson
final case class FruitBasket(
    @jsonField("f") item: Fruit,
    @jsonField("nut") nutrition: Nutrition
)
object FruitBasket {
  val JsonFields = generateJsonFields[FruitBasket]
}

class ZioJsonFruitSpec extends munit.FunSuite {

  test("zio-json @jsonField renames fields on Fruit") {
    assertEquals(Fruit.JsonFields._id, "_id")
    assertEquals(Fruit.JsonFields.variety, "v")
    assertEquals(Fruit.JsonFields.origin, "o")
    assertEquals(Fruit.JsonFields.producer, "p")
  }

  test("zio-json @jsonField supports nested case classes") {
    assertEquals(FruitBasket.JsonFields.item._path, "f")
    assertEquals(FruitBasket.JsonFields.item.variety, "f.v")
    assertEquals(FruitBasket.JsonFields.nutrition._path, "nut")
    assertEquals(FruitBasket.JsonFields.nutrition.calories, "nut.cal")
  }
}
