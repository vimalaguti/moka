package io.moka

/** 2.13-only fixtures for the composition contract, compiled in the main run so
  * the test run sees them only through classfiles.
  */

/** A descendable type with no moka object at all (spec rule 4). */
final case class S2ClassfilePlain(a: Int)

/** A hand-written object without a marker — like one compiled by moka 0.1.x —
  * is not a candidate.
  */
final case class S2UnmarkedInner(a: Int)
object S2UnmarkedInner {
  object Fields {
    val a: "a" = "a"
  }
}

/** Two BSON-valid objects with different members (spec rule 2). */
@moka
final case class S2AmbiguousInner(a: String)
object S2AmbiguousInner {
  val Fields = generateFields[S2AmbiguousInner]
  object Manual extends MokaBsonNames {
    val a: "x" = "x"
  }
}

/** Two levels below an outer from another run, referenced by nothing else, so
  * `S2DeepLeaf`'s classfile symbol is still unloaded when the outer expands.
  */
@moka
final case class S2DeepLeaf(x: Int)
object S2DeepLeaf {
  val Fields = generateFields[S2DeepLeaf]
}

@moka
final case class S2DeepMid(leaf: S2DeepLeaf)
object S2DeepMid {
  val Fields = generateFields[S2DeepMid]
}
