package io.moka

import org.mongodb.scala.bson.annotations.BsonProperty

/** Wider than 22 fields, the old tuple/function arity limit. */
@moka
final case class Wide30(
    @BsonProperty("renamed_f1") f1: String,
    f2: Int,
    f3: Int,
    f4: Int,
    f5: Int,
    f6: Int,
    f7: Int,
    f8: Int,
    f9: Int,
    f10: Int,
    f11: Int,
    f12: Int,
    f13: Int,
    f14: Int,
    f15: Int,
    f16: Int,
    f17: Int,
    f18: Int,
    f19: Int,
    f20: Int,
    f21: Int,
    f22: Int,
    f23: Int,
    f24: Int,
    f25: Int,
    f26: Int,
    f27: Int,
    f28: Int,
    f29: Int,
    f30: Int
)
object Wide30 {
  val Fields = generateFields[Wide30]
}

@moka
final case class WideOuter(
    o1: Int,
    o2: Int,
    o3: Int,
    o4: Int,
    o5: Int,
    o6: Int,
    o7: Int,
    o8: Int,
    o9: Int,
    o10: Int,
    o11: Int,
    o12: Int,
    o13: Int,
    o14: Int,
    o15: Int,
    o16: Int,
    o17: Int,
    o18: Int,
    o19: Int,
    o20: Int,
    o21: Int,
    o22: Int,
    o23: Int,
    o24: Int,
    wide: Wide30
)
object WideOuter {
  val Fields = generateFields[WideOuter]
}
