package io.moka

/** Marker of a generated names object that is valid for every format: no field
  * anywhere in it is renamed by any format's annotation.
  */
trait MokaNames

/** Marker of a generated names object built for BSON (`generateFields`,
  * `generateBsonFields`, `@moka`, `@mokaBson`).
  */
trait MokaBsonNames

/** Marker of a generated names object built for JSON (`generateJsonFields`,
  * `@mokaJson`).
  */
trait MokaJsonNames
