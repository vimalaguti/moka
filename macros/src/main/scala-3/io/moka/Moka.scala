package io.moka

import scala.quoted.*
import scala.annotation.compileTimeOnly
import scala.annotation.StaticAnnotation

object Moka:
  @deprecated("Placeholder. For scala 3, use generateFields macro.")
  @compileTimeOnly("not required for scala 3")
  class moka(name: String = "Fields") extends StaticAnnotation

  inline def generateFields[T]: Any = ${ generateFieldsImpl[T] }

  private def generateFieldsImpl[T: Type](using Quotes): Expr[Any] =
    import quotes.reflect.*
    val tpe = TypeRepr.of[T]
    // val fields = tpe.typeSymbol.caseFields.map { field =>
    //   val fieldName = field.name
    //   ValDef(Symbol.newVal(Symbol.spliceOwner, fieldName, TypeTree.of[String].tpe, Flags.Final), Some(Literal(StringConstant(fieldName))))
    // }
    // val fieldsObject = ClassDef(
    //   Symbol.newClass(Symbol.spliceOwner, "Fields", Flags.Module, List(TypeTree.of[Object].tpe)),
    //   List(DefDef(Symbol.noSymbol, List(), TypeTree.of[Unit], Some(Block(fields, Literal(UnitConstant())))))
    // )
    // val result = Block(List(fieldsObject), Literal(UnitConstant()))
    // result.asExpr
