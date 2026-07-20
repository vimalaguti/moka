package io.moka

import scala.annotation.StaticAnnotation
import scala.quoted.*

object Moka:
  /** No-op on Scala 3: kept so cross-compiled sources can annotate case classes
    * for the Scala 2 macro. Field generation happens via [[generateFields]].
    */
  class moka(name: String = "Fields") extends StaticAnnotation

  class FieldNames(values: Map[String, String]) extends Selectable:
    def selectDynamic(name: String): String = values(name)

  transparent inline def generateFields[T]: FieldNames = ${
    generateFieldsImpl[T]
  }

  private def generateFieldsImpl[T: Type](using Quotes): Expr[FieldNames] =
    import quotes.reflect.*

    val classSym = TypeRepr.of[T].typeSymbol
    if !classSym.flags.is(Flags.Case) then
      report.errorAndAbort(
        s"Moka.generateFields[${classSym.name}] requires a case class"
      )

    def bsonName(field: Symbol): String =
      val ctorParam = classSym.primaryConstructor.paramSymss.flatten
        .find(_.name == field.name)
      (field.annotations ++ ctorParam.toList.flatMap(_.annotations))
        .collectFirst {
          case ann @ Apply(_, List(Literal(StringConstant(value))))
              if ann.tpe.typeSymbol.name == "BsonProperty" || ann.tpe.typeSymbol.name == "bsonField" =>
            value
        }
        .getOrElse(field.name)

    val namesAndValues =
      classSym.caseFields.map(field => field.name -> bsonName(field))
    // refine with the literal type of the value (like the Scala 2 macro),
    // e.g. `val a: "abc"` for @BsonProperty("abc") a
    val refined = namesAndValues.foldLeft(TypeRepr.of[FieldNames]) {
      case (tpe, (fieldName, value)) =>
        Refinement(tpe, fieldName, ConstantType(StringConstant(value)))
    }
    val values = Expr(namesAndValues.toMap)
    refined.asType match
      case '[t] => '{ new FieldNames($values).asInstanceOf[t & FieldNames] }
