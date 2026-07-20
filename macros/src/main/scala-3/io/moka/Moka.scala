package io.moka

import scala.annotation.{MacroAnnotation, experimental}
import scala.quoted.*

object Moka:
  @experimental
  class moka(name: String = "Fields") extends MacroAnnotation:
    override def transform(using Quotes)(
      definition: quotes.reflect.Definition,
      companion: Option[quotes.reflect.Definition]
    ): List[quotes.reflect.Definition] =
      import quotes.reflect.*

      def bsonName(field: Symbol): String =
        field.annotations.collectFirst {
          case ann @ Apply(_, List(Literal(StringConstant(value))))
              if ann.tpe.typeSymbol.name == "BsonProperty" || ann.tpe.typeSymbol.name == "bsonField" =>
            value
        }.getOrElse(field.name)

      def fieldsDefs(owner: Symbol, classSym: Symbol): List[Statement] =
        val fields = classSym.caseFields
        val namesAndValues = fields.map(field => field.name -> bsonName(field))
        val fieldsModule = Symbol.newModule(
          owner,
          name,
          Flags.EmptyFlags,
          Flags.EmptyFlags,
          List(TypeRepr.of[Object]),
          cls =>
            namesAndValues.map { case (fieldName, _) =>
              Symbol.newMethod(cls, fieldName, TypeRepr.of[String], Flags.EmptyFlags, Symbol.noSymbol)
            },
          Symbol.noSymbol
        )
        val fieldsClass = fieldsModule.moduleClass
        val fieldsMembers = fieldsClass.declaredMethods

        val fieldsClassDef = ClassDef(
          fieldsClass,
          List(TypeTree.of[Object]),
          fieldsMembers.zip(namesAndValues).map { case (fieldSym, (_, value)) =>
            DefDef(fieldSym, _ => Some(Literal(StringConstant(value))))
          }
        )
        val fieldsModuleDef = ValDef(
          fieldsModule,
          Some(Apply(Select(New(TypeIdent(fieldsClass)), fieldsClass.primaryConstructor), Nil))
        )
        List(fieldsClassDef, fieldsModuleDef)

      definition match
        case classDef: ClassDef if classDef.symbol.flags.is(Flags.Case) =>
          val updatedCompanion = companion match
            case Some(moduleDef: ClassDef) if moduleDef.symbol.flags.is(Flags.Module) =>
              val owner = moduleDef.symbol
              val generatedFields = fieldsDefs(owner, classDef.symbol)
              Some(
                ClassDef.copy(moduleDef)(
                  moduleDef.name,
                  moduleDef.constructor,
                  moduleDef.parents,
                  moduleDef.self,
                  generatedFields ::: moduleDef.body
                )
              )
            case None =>
              report.errorAndAbort("@moka on Scala 3 currently requires an explicit companion object", classDef.pos)
            case _ =>
              report.errorAndAbort("@moka only supports classes with object companions", classDef.pos)

          List(classDef, updatedCompanion.get)
        case _ =>
          report.error("@moka only supports case classes", definition.pos)
          List(definition)

  class FieldNames(values: Map[String, String]) extends Selectable:
    def selectDynamic(name: String): String = values(name)

  transparent inline def generateFields[T]: FieldNames = ${ generateFieldsImpl[T] }

  private def generateFieldsImpl[T: Type](using Quotes): Expr[FieldNames] =
    import quotes.reflect.*

    val classSym = TypeRepr.of[T].typeSymbol
    if !classSym.flags.is(Flags.Case) then
      report.errorAndAbort(s"Moka.generateFields[${classSym.name}] requires a case class")

    def bsonName(field: Symbol): String =
      val ctorParam = classSym.primaryConstructor.paramSymss.flatten.find(_.name == field.name)
      (field.annotations ++ ctorParam.toList.flatMap(_.annotations)).collectFirst {
        case ann @ Apply(_, List(Literal(StringConstant(value))))
            if ann.tpe.typeSymbol.name == "BsonProperty" || ann.tpe.typeSymbol.name == "bsonField" =>
          value
      }.getOrElse(field.name)

    val namesAndValues = classSym.caseFields.map(field => field.name -> bsonName(field))
    val refined = namesAndValues.foldLeft(TypeRepr.of[FieldNames]) { case (tpe, (fieldName, _)) =>
      Refinement(tpe, fieldName, TypeRepr.of[String])
    }
    val values = Expr(namesAndValues.toMap)
    refined.asType match
      case '[t] => '{ new FieldNames($values).asInstanceOf[t & FieldNames] }
