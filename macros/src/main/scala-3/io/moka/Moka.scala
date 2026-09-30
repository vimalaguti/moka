package io.moka

import scala.annotation.StaticAnnotation
import scala.quoted.*

/** No-op on Scala 3: kept so cross-compiled sources can annotate case classes
  * for the Scala 2 macro. Field generation happens via [[generateFields]].
  */
class moka(name: String = "Fields")         extends StaticAnnotation
class mokaBson(name: String = "BsonFields") extends StaticAnnotation
class mokaJson(name: String = "JsonFields") extends StaticAnnotation

enum RenamingMode:
  case BsonOnly, JsonOnly

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
  generateImpl[T](RenamingMode.BsonOnly)

private def generateBsonFieldsImpl[T: Type](using Quotes): Expr[FieldNames] =
  generateImpl[T](RenamingMode.BsonOnly)

private def generateJsonFieldsImpl[T: Type](using Quotes): Expr[FieldNames] =
  generateImpl[T](RenamingMode.JsonOnly)

private def generateImpl[T: Type](mode: RenamingMode)(using
    Quotes
): Expr[FieldNames] =
  import quotes.reflect.*

  val rootTpe = TypeRepr.of[T]
  if !rootTpe.typeSymbol.flags.is(Flags.Case) then
    report.errorAndAbort(
      s"generateFields[${rootTpe.typeSymbol.name}] requires a case class"
    )

  val bsonAnnotations = Set("BsonProperty", "bsonField")
  val jsonAnnotations = Set("jsonField", "JsonKey", "JsonProperty")

  def bsonName(owner: Symbol, field: Symbol): String =
    val ctorParam =
      owner.primaryConstructor.paramSymss.flatten.find(_.name == field.name)
    val allAnnotations =
      field.annotations ++ ctorParam.toList.flatMap(_.annotations)
    val extracted = allAnnotations.collect {
      case ann @ Apply(_, List(Literal(StringConstant(value)))) =>
        (ann.tpe.typeSymbol.name, value)
    }.distinct

    val filtered = mode match
      case RenamingMode.BsonOnly =>
        extracted.filter((ann, _) => bsonAnnotations.contains(ann))
      case RenamingMode.JsonOnly =>
        extracted.filter((ann, _) => jsonAnnotations.contains(ann))

    val distinctValues = filtered.map(_._2).distinct
    if distinctValues.isEmpty then field.name
    else if distinctValues.length == 1 then distinctValues.head
    else
      val formatted =
        filtered.map((ann, v) => s"@$ann(\"$v\")").mkString(", ")
      val target = mode match
        case RenamingMode.BsonOnly => "BSON"
        case RenamingMode.JsonOnly => "JSON"
      report.errorAndAbort(
        s"moka: conflicting $target renaming annotations on field '${field.name}': $formatted."
      )

  /** Case classes are descended into; value classes are not (a value class is
    * stored flattened, so its path is the outer field's path).
    */
  def isDescendable(t: TypeRepr): Boolean =
    val s = t.dealias.typeSymbol
    s.isClassDef && s.flags.is(Flags.Case) && !(t.dealias <:< TypeRepr
      .of[AnyVal])

  val pathNode  = Symbol.classSymbol("io.moka.PathNode")
  val optionSym = TypeRepr.of[Option[Any]].typeSymbol

  /** `Option` and single-element collections are transparent: MongoDB's dot
    * notation is the same whether a sub-document is optional, in an array, or
    * neither. `Map` has two type arguments and is left alone.
    */
  def unwrap(t: TypeRepr, sawCollection: Boolean = false): (TypeRepr, Boolean) =
    val d = t.dealias
    d match
      case AppliedType(_, List(arg)) if d.typeSymbol == optionSym =>
        unwrap(arg, sawCollection)
      case AppliedType(_, List(arg)) if d <:< TypeRepr.of[Iterable[Any]] =>
        unwrap(arg, true)
      case _ => (d, sawCollection)

  /** Returns the refined type of the node addressing `prefix`, paired with the
    * expression building it. `prefix` is empty only for the root.
    */
  def build(
      tpe: TypeRepr,
      prefix: String,
      seen: Set[String],
      isArray: Boolean
  ): (TypeRepr, Expr[Any]) =
    val owner = tpe.dealias.typeSymbol
    val entries = owner.caseFields.map { field =>
      val path =
        if prefix.isEmpty then bsonName(owner, field)
        else prefix + "." + bsonName(owner, field)
      val (fieldTpe, fieldIsArray) = unwrap(tpe.dealias.memberType(field))
      val key                      = fieldTpe.typeSymbol.fullName
      if isDescendable(fieldTpe) && !seen.contains(key) then
        val (childTpe, childExpr) =
          build(fieldTpe, path, seen + key, fieldIsArray)
        (field.name, childTpe, childExpr)
      else
        (field.name, ConstantType(StringConstant(path)), Expr(path): Expr[Any])
    }

    // MongoDB's array operators. Neither is itself an array, so they do not
    // nest further.
    val arrayOps =
      if isArray then
        List("_matched" -> (prefix + ".$"), "_all" -> (prefix + ".$[]")).map {
          (name, opPath) =>
            val (opTpe, opExpr) = build(tpe, opPath, seen, false)
            (name, opTpe, opExpr)
        }
      else Nil
    val members = entries ++ arrayOps

    val base =
      if prefix.isEmpty then TypeRepr.of[FieldNames]
      else pathNode.typeRef.appliedTo(ConstantType(StringConstant(prefix)))
    val refined = members.foldLeft(base) { case (acc, (name, tpe, _)) =>
      Refinement(acc, name, tpe)
    }

    val children = Expr.ofList(members.map { case (name, _, value) =>
      '{ (${ Expr(name) }, $value) }
    })
    val node =
      if prefix.isEmpty then '{ FieldNames($children.toMap) }
      else '{ PathNode(${ Expr(prefix) }, $children.toMap) }
    (refined, node)

  val (refined, node) =
    build(rootTpe, "", Set(rootTpe.typeSymbol.fullName), isArray = false)
  refined.asType match
    case '[t] =>
      '{ ${ node.asExprOf[FieldNames] }.asInstanceOf[t & FieldNames] }
