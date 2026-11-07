package io.moka

import scala.annotation.StaticAnnotation
import scala.quoted.*

/** No-op on Scala 3: kept so cross-compiled sources can annotate case classes
  * for the Scala 2 macro. Field generation happens via [[generateFields]].
  */
class moka(name: String = "Fields")         extends StaticAnnotation
class mokaBson(name: String = "BsonFields") extends StaticAnnotation
class mokaJson(name: String = "JsonFields") extends StaticAnnotation

transparent inline def generateFields[T]: FieldNames = ${
  generateFieldsImpl[T]
}

transparent inline def generateBsonFields[T]: FieldNames = ${
  generateBsonFieldsImpl[T]
}

transparent inline def generateJsonFields[T]: FieldNames = ${
  generateJsonFieldsImpl[T]
}

private def generateFieldsImpl[T: Type](using Quotes): Expr[FieldNames] =
  generateImpl[T](MokaFormat.Bson, "generateFields")

private def generateBsonFieldsImpl[T: Type](using Quotes): Expr[FieldNames] =
  generateImpl[T](MokaFormat.Bson, "generateBsonFields")

private def generateJsonFieldsImpl[T: Type](using Quotes): Expr[FieldNames] =
  generateImpl[T](MokaFormat.Json, "generateJsonFields")

private def generateImpl[T: Type](format: MokaFormat, methodName: String)(using
    Quotes
): Expr[FieldNames] =
  import quotes.reflect.*

  val rootTpe = TypeRepr.of[T].dealias
  if !rootTpe.typeSymbol.flags.is(Flags.Case) then
    report.errorAndAbort(
      s"moka: $methodName[${rootTpe.typeSymbol.name}] requires a case class."
    )

  /** Whether any field built into the object carries a renaming annotation of
    * any format (spec rule 1); decides the neutral marker.
    */
  var renamed = false

  // the root's own fields renamed for this format, and for the other one
  val ownRenamed   = scala.collection.mutable.ListBuffer.empty[String]
  val otherRenamed = scala.collection.mutable.ListBuffer.empty[String]

  /** Where to report about `field`: the field itself, as Scala 2 does. */
  def fieldPos(field: Symbol): Position =
    field.pos.getOrElse(Position.ofMacroExpansion)
  var otherPos = Position.ofMacroExpansion

  def bsonName(owner: Symbol, field: Symbol): String =
    val ctorParam =
      owner.primaryConstructor.paramSymss.flatten.find(_.name == field.name)
    val allAnnotations =
      field.annotations ++ ctorParam.toList.flatMap(_.annotations)
    def extractStringArg(tree: Term): Option[String] = tree match
      case Apply(fun, args) =>
        args
          .collectFirst {
            case Literal(StringConstant(value))              => value
            case NamedArg(_, Literal(StringConstant(value))) => value
          }
          .orElse(extractStringArg(fun))
      case _ => None

    val extracted = MokaRules.renames(allAnnotations.flatMap { ann =>
      extractStringArg(ann).map(v => (ann.tpe.typeSymbol.name, v))
    })
    if extracted.nonEmpty then renamed = true

    if owner == rootTpe.typeSymbol then
      if extracted.exists(r => format.renaming(r._1)) then
        ownRenamed += field.name
      else if extracted.nonEmpty then
        if otherRenamed.isEmpty then otherPos = fieldPos(field)
        otherRenamed += field.name

    MokaRules.resolveRename(field.name, extracted, format) match
      case Right(name) => name
      case Left(msg)   => report.errorAndAbort(msg, fieldPos(field))

  /** Case classes are descended into; value classes are not (a value class is
    * stored flattened, so its path is the outer field's path).
    */
  // Same rule as `isDescendable` in Scala 2 `MokaExpander.scala`: keep them in step.
  def isDescendable(t: TypeRepr): Boolean =
    val s = t.dealias.typeSymbol
    s.isClassDef && s.flags.is(Flags.Case) && !(t.dealias <:< TypeRepr
      .of[AnyVal])

  val pathNode  = Symbol.classSymbol("io.moka.PathNode")
  val optionSym = TypeRepr.of[Option[Any]].typeSymbol
  val mapSym    = Symbol.requiredClass("scala.collection.Map")

  /** `Option` and single-element collections are transparent: MongoDB's dot
    * notation is the same whether a sub-document is optional, in an array, or
    * neither. A `Map` is never one, even with one type argument (`IntMap[V]`).
    */
  // Same rule as `unwrap` in Scala 2 `MokaExpander.scala`: keep them in step.
  def unwrap(t: TypeRepr, sawCollection: Boolean = false): (TypeRepr, Boolean) =
    val d = t.dealias
    d match
      case AppliedType(_, List(arg)) if d.typeSymbol == optionSym =>
        unwrap(arg, sawCollection)
      case AppliedType(_, List(arg))
          if d <:< TypeRepr
            .of[Iterable[Any]] && !d.baseClasses.contains(mapSym) =>
        unwrap(arg, true)
      case _ => (d, sawCollection)

  def size(t: TypeRepr): Int = t.dealias match
    case AppliedType(_, args) => 1 + args.map(size).sum
    case _                    => 1

  /** Whether descending into `t` would repeat a type on `path` (nearest first).
    * A generic type may nest a strictly smaller instance of itself
    * (`Box[Box[X]]`); a growing one (`F[A](x: F[List[A]])`) stops, so every
    * descent terminates.
    */
  // Same rule as `onPath` in Scala 2 `MokaExpander.scala`: keep them in step.
  def onPath(t: TypeRepr, path: List[TypeRepr]): Boolean =
    path.find(_.typeSymbol == t.typeSymbol).exists(p => size(t) >= size(p))

  /** Returns the refined type of the node addressing `prefix`, paired with its
    * encoding for `FieldNames` to decode. `prefix` is empty only for the root.
    */
  def build(
      tpe: TypeRepr,
      prefix: String,
      seen: List[TypeRepr],
      isArray: Boolean
  ): (TypeRepr, String) =
    val owner = tpe.dealias.typeSymbol
    def member(name: String, seg: String, kind: String) =
      name + FieldNames.Sep + seg + FieldNames.Sep + kind
    val entries = owner.caseFields.map { field =>
      val seg  = bsonName(owner, field)
      val path = if prefix.isEmpty then seg else prefix + "." + seg
      val (fieldTpe, fieldIsArray) = unwrap(tpe.dealias.memberType(field))
      if isDescendable(fieldTpe) && !onPath(fieldTpe, seen) then
        val (childTpe, child) =
          build(fieldTpe, path, fieldTpe :: seen, fieldIsArray)
        (field.name, childTpe, member(field.name, seg, "N" + child))
      else
        (
          field.name,
          ConstantType(StringConstant(path)),
          member(field.name, seg, "L")
        )
    }

    // MongoDB's array operators. Neither is itself an array, so they do not
    // nest further.
    val arrayOps =
      if isArray then
        MokaRules.arrayOperators.map { (name, seg) =>
          val (opTpe, op) = build(tpe, prefix + "." + seg, seen, false)
          (name, opTpe, member(name, seg, "N" + op))
        }
      else Nil
    val members = entries ++ arrayOps

    // Only the root carries a marker. Every descendant is built above, so
    // `renamed` is final by the time the root reads it.
    val base =
      if prefix.isEmpty then
        if !renamed then TypeRepr.of[FieldNames & MokaNames]
        else
          format match
            case MokaFormat.Bson => TypeRepr.of[FieldNames & MokaBsonNames]
            case MokaFormat.Json => TypeRepr.of[FieldNames & MokaJsonNames]
      else pathNode.typeRef.appliedTo(ConstantType(StringConstant(prefix)))
    val refined = members.foldLeft(base) { case (acc, (name, tpe, _)) =>
      Refinement(acc, name, tpe)
    }
    (refined, members.length.toString + ";" + members.map(_._3).mkString)

  val (refined, tree) =
    build(rootTpe, "", List(rootTpe), isArray = false)

  // A JVM string constant holds at most 65535 bytes of modified UTF-8, where a
  // char takes up to 3; a split surrogate pair is rejoined on decoding.
  val chunks = Varargs(tree.grouped(20000).map(Expr(_)).toList)
  val root =
    if !renamed then '{ FieldNames.neutral($chunks*) }
    else
      format match
        case MokaFormat.Bson => '{ FieldNames.bson($chunks*) }
        case MokaFormat.Json => '{ FieldNames.json($chunks*) }

  if ownRenamed.isEmpty && otherRenamed.nonEmpty then
    val other = format.other
    report.warning(
      s"moka: ${rootTpe.typeSymbol.name}'s fields are ${MokaRules.renamedOnlyBy(other)} " +
        s"(${otherRenamed.mkString(", ")}), which $methodName ignores for ${format.label} names: its names are " +
        s"the Scala field names. If the documents use the ${other.label} names, use ${other.explicitMethod}.",
      otherPos
    )
  refined.asType match
    case '[t] => '{ $root.asInstanceOf[t & FieldNames] }
