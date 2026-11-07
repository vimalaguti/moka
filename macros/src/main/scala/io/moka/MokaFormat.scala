package io.moka

/** A names format, and the names a user writes for it. Shared by the Scala 2
  * and Scala 3 macros, which are otherwise separate implementations: only the
  * user-facing surface and plain string rules live here.
  *
  * `@moka` / `generateFields` / `Fields` are the default spellings and produce
  * BSON; `@mokaBson` / `generateBsonFields` / `BsonFields` are BSON's explicit
  * ones. JSON has only its explicit spellings.
  */
private[moka] sealed abstract class MokaFormat(
    /** How messages name the format. */
    val label: String,
    /** Simple names of the annotations that rename a field for this format. */
    val renaming: Set[String],
    /** The moka annotation, generate method and object name messages suggest.
      */
    val annotation: String,
    val method: String,
    val defaultObject: String,
    /** The spellings that name the format explicitly. */
    val explicitAnnotation: String,
    val explicitMethod: String,
    val explicitObject: String
) {
  def other: MokaFormat

  /** Each moka annotation of this format, with the object it generates when
    * given no name.
    */
  def annotations: Map[String, String] =
    Map(annotation -> defaultObject, explicitAnnotation -> explicitObject)

  def methods: Set[String] = Set(method, explicitMethod)
}

private[moka] object MokaFormat {
  case object Bson
      extends MokaFormat(
        label = "BSON",
        renaming = Set("BsonProperty", "bsonField"),
        annotation = "moka",
        method = "generateFields",
        defaultObject = "Fields",
        explicitAnnotation = "mokaBson",
        explicitMethod = "generateBsonFields",
        explicitObject = "BsonFields"
      ) {
    def other: MokaFormat = Json
  }

  case object Json
      extends MokaFormat(
        label = "JSON",
        renaming = Set("jsonField"),
        annotation = "mokaJson",
        method = "generateJsonFields",
        defaultObject = "JsonFields",
        explicitAnnotation = "mokaJson",
        explicitMethod = "generateJsonFields",
        explicitObject = "JsonFields"
      ) {
    def other: MokaFormat = Bson
  }

  val all: List[MokaFormat] = List(Bson, Json)

  /** Simple names of every renaming annotation, of any format. */
  val allRenaming: Set[String] = all.flatMap(_.renaming).toSet

  /** The format a generate method produces. */
  def ofMethod(name: String): Option[MokaFormat] =
    all.find(_.methods(name))

  /** The format a moka annotation produces, and its default object name. */
  def ofAnnotation(name: String): Option[(MokaFormat, String)] =
    all.flatMap(f => f.annotations.get(name).map(o => (f, o))).headOption
}

/** Rules both macros apply identically, over plain strings. Reading
  * annotations, types and trees stays in each version: their reflection APIs
  * differ.
  */
private[moka] object MokaRules {

  /** A field's renaming annotations as (simple name, value), keeping only
    * renaming ones of any format. An empty value is no annotation at all (spec
    * rule 5); the same annotation read twice (from the field and its
    * constructor parameter) counts once.
    */
  def renames(read: List[(String, String)]): List[(String, String)] =
    read
      .filter(r => MokaFormat.allRenaming(r._1) && r._2.nonEmpty)
      .distinct

  /** The name of `field` in `format`, from its [[renames]]: the field's own
    * name when none applies, or the conflict message when they disagree.
    */
  def resolveRename(
      field: String,
      renames: List[(String, String)],
      format: MokaFormat
  ): Either[String, String] = {
    val matches = renames.filter(r => format.renaming(r._1))
    matches.map(_._2).distinct match {
      case Nil          => Right(field)
      case value :: Nil => Right(value)
      case _ =>
        val formatted =
          matches.map { case (ann, v) => s"@$ann(\"$v\")" }.mkString(", ")
        Left(
          s"moka: conflicting ${format.label} renaming annotations on field '$field': $formatted."
        )
    }
  }

  /** MongoDB's array operators, as (generated member, path segment). */
  val arrayOperators: List[(String, String)] =
    List("_matched" -> "$", "_all" -> "$[]")

  /** The part of the "renamed only by the other format" warning that users
    * match in `@nowarn("msg=...")`, so both versions must word it alike.
    */
  def renamedOnlyBy(other: MokaFormat): String =
    s"renamed only by ${other.label} annotations"
}
