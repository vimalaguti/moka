package io.moka

import org.mongodb.scala.bson.annotations.BsonProperty
import zio.bson.bsonField

/** Scala 3-only style: `generateFields[T]` in the companion, no @moka
  * annotation. This is the cleanest way to use moka on Scala 3, but it does not
  * cross-compile: the Scala 2 macro only rewrites the placeholder val when the
  * case class is annotated with @moka.
  */
object Scala3Definitions {
  final case class PlainFields(a: Int, b: Int)
  object PlainFields {
    val Fields = generateFields[PlainFields]
  }

  final case class BsonFields(
      @BsonProperty("renamed") a: Int,
      @bsonField("znamed") b: Int
  )
  object BsonFields {
    val Fields = generateFields[BsonFields]
  }

  // the generated object's name is simply the val's name
  final case class CustomName(a: Int)
  object CustomName {
    val Renamed = generateFields[CustomName]
  }

  final case class WithMembers(a: Int)
  object WithMembers {
    val default: WithMembers = WithMembers(0)
    val Fields               = generateFields[WithMembers]
  }

  final case class Inner(x: Int)
  final case class Nested(inner: Inner)
  object Nested {
    val Fields = generateFields[Nested]
  }

  // Scala 3 has no restriction on where the nested type is declared.
  final case class S3Item(@BsonProperty("q") qty: Int)
  final case class S3Holder(items: List[S3Item])
  object S3Holder {
    val Fields = generateFields[S3Holder]
  }

  final case class S3FormatBson(@BsonProperty("b") name: String)
  object S3FormatBson {
    val BsonFields = generateBsonFields[S3FormatBson]
  }

  final case class S3FormatJson(@zio.json.jsonField("j") name: String)
  object S3FormatJson {
    val JsonFields = generateJsonFields[S3FormatJson]
  }
}

/** Scala 3 never composes: a nested type without any moka object, read from the
  * test run through TASTy, still yields its renamed names.
  */
final case class S3PlainRenamedInner(
    @bsonField("zb") a: String,
    @zio.json.jsonField("zj") b: String
)

/** A two-type cycle where both sides have their own object. Shape is decided by
  * the outer's path, so `S3CycleOuter.Fields.n.o` is a leaf.
  */
final case class S3CycleOuter(n: S3CycleN, x: Int)
object S3CycleOuter {
  val Fields = generateFields[S3CycleOuter]
}
final case class S3CycleN(o: S3CycleOuter, y: Int)
object S3CycleN {
  val Fields = generateFields[S3CycleN]
}

/** A type alias as the root is dealiased. */
final case class S3Aliased(@BsonProperty("r") a: Int)
type S3Alias = S3Aliased
object S3AliasUse {
  val Fields = generateFields[S3Alias]
}

/** A generic type nesting a smaller instance of itself is descended into. */
final case class S3Box[T](value: T)
final case class S3Leaf(@BsonProperty("xx") x: Int)
final case class S3BoxBox(b: S3Box[S3Box[S3Leaf]])
object S3BoxBox {
  val Fields = generateFields[S3BoxBox]
}

/** Polymorphic recursion grows the type at each level: it must stop. */
final case class S3Grow[A](next: Option[S3Grow[List[A]]], a: Option[A])
final case class S3GrowRoot(g: S3Grow[S3Leaf])
object S3GrowRoot {
  val Fields = generateFields[S3GrowRoot]
}

/** Opaque types are leaves, even over a case class. */
object S3Opaques {
  opaque type Code = String
  object Code {
    def apply(s: String): Code = s
  }
  opaque type Boxed = S3PlainRenamedInner
  object Boxed {
    def apply(i: S3PlainRenamedInner): Boxed = i
  }
}
