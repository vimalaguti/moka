package io.moka

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

trait Moka[T] {

  // def generateFields

  // val Fields
}

object Moka {

  @compileTimeOnly("enable macro paradise to expand macro annotations")
  class moka extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro mokaMacro.impl
  }

  object mokaMacro {
    def impl(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
      import c.universe._

      def extractCaseClassParts(
          classDecl: ClassDef
      ): (TypeName, List[List[ValDef]]) =
        classDecl match {
          case q"$mods class $tpname[..$tparams] $ctorMods(...$paramss) extends { ..$earlydefns } with ..$parents { $self => ..$stats }" =>
            if (mods.hasFlag(Flag.CASE)) (tpname, paramss)
            else
              c.abort(
                c.enclosingPosition,
                "Class is not a case class: " + tpname
              )
          case _ => c.abort(c.enclosingPosition, "Invalid class " + classDecl)
        }

      def generateFieldNames(terms: List[ValDef]) = {
        terms.map {
          case t @ q"$mods val $name: $tpt = $rhs" =>
            val termName = TermName(name.decodedName.toString())
            val value = q"${name.decodedName.toString()}"
            val tpe = tq"${name.decodedName.toString()}"

            val definition = ValDef(Modifiers(), termName, tpe, value)

            println(s"TERM DEFINITION $name:" + definition)
            definition
          case term =>
            c.abort(c.enclosingPosition, "Invalid field: " + term.name)
        }
      }

      annottees.map(_.tree).toList match {
        case (classDecl: ClassDef) :: Nil =>
          val (className, fields) = extractCaseClassParts(classDecl)

          // generate the names
          val generatedTerms = generateFieldNames(fields.head)

          // generate Fields object
          val objectFields = q"object Fields { ..$generatedTerms }"

          val companion =
            q"""
            $classDecl // original class
            object ${className.toTermName} {
              $objectFields
            }
            """
          println("COMPANION: " + companion)
          c.Expr[Any](companion)
        case _ => c.abort(c.enclosingPosition, "Invalid annottee")
      }
    }
  }

}
