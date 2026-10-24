package io.moka.reviewv1

import io.moka._
import zio.bson.bsonField

/** Same simple name as io.moka.Scala2ReviewUser's outer type, other package. */
@moka
final case class User(@bsonField("n") name: String)
object User {
  val Fields = generateFields[User]
}
