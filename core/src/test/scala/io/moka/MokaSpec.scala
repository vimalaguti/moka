package io.moka

import Moka.moka
import io.moka.TestDefinitions._
import org.mongodb.scala.bson.annotations.BsonProperty
import zio.bson.bsonField

class MokaSpec extends munit.FunSuite {

  test("no fields") {
    NoFields.Fields
  }

  test("one field") {
    assertEquals(OneField.Fields.a, "a")
  }

  test("two fields") {
    assertEquals(ManyFields.Fields.a, "a")
    assertEquals(ManyFields.Fields.b, "b")
  }

  test("two fields primitive types") {
    assertEquals(DiffFields.Fields.a, "a")
    assertEquals(DiffFields.Fields.b, "b")
  }

  test("non primitive type") {
    assertEquals(NestedSimple.Fields.a, "a")
  }

  test("supports defined companion object") {
    assertEquals(WithCompanionObject.b, 3)
    assertEquals(WithCompanionObject.someFunction(3), WithCompanionObject(3))
    assertEquals(WithCompanionObject.Fields.a, "a")
  }

  test("supports bson annotations") {
    assertEquals(BsonAnnotatedClass(1).a, 1)
    assertEquals(BsonAnnotatedClass.Fields.a, "b")
  }

  test("supports zio bson annotations") {
    assertEquals(ZioBsonAnnotatedClass(1).a, 1)
    assertEquals(ZioBsonAnnotatedClass.Fields.a, "b")
  }

  test("annotations works with companion object") {
    assertEquals(BsonAnnotatedClassCO.Fields.a, "b")
    assertEquals(BsonAnnotatedClassCO.a, 0)
    assertEquals(ZioBsonAnnotatedClassCO.Fields.a, "b")
    assertEquals(ZioBsonAnnotatedClassCO.a, 0)
  }

  test("supports a simple nested case classe") {
    // assertEquals(NestedClass.Fields.one.name, "one")
    // assertEquals(NestedClass.Fields.one.a, "a")
  }

}

object TestDefinitions {

  @moka
  final case class NoFields()
  @moka
  final case class OneField(a: Int)
  @moka
  final case class ManyFields(a: Int, b: Int)
  @moka
  final case class DiffFields(a: Int, b: String)
  case class A(value: Int) extends AnyVal
  @moka
  case class NestedSimple(a: A)
  @moka
  case class WithCompanionObject(a: Int)
  object WithCompanionObject {
    val b = 3
    def someFunction(a: Int): WithCompanionObject = WithCompanionObject(a)
  }
  @moka
  final case class BsonAnnotatedClass(@BsonProperty("b") a: Int)
  @moka
  final case class ZioBsonAnnotatedClass(@bsonField("b") a: Int)

  @moka
  final case class BsonAnnotatedClassCO(@BsonProperty("b") a: Int)
  object BsonAnnotatedClassCO {
    val a = 0
  }
  @moka
  final case class ZioBsonAnnotatedClassCO(@bsonField("b") a: Int) 
  object ZioBsonAnnotatedClassCO {
    val a = 0
  }

  @moka
  final case class NestedClass(one: OneField)

}
