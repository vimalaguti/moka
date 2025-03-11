package io.moka

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.whitebox


object Moka {

  @compileTimeOnly("enable macro paradise to expand macro annotations")
  class moka(name: String = "Fields") extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro mokaMacro.impl
  }

  object mokaMacro {
    def impl(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
      import c.universe._

      def extractObjectDestinationName: TermName = 
        c.prefix.tree match {
          case Apply(_, Literal(Constant(name: String)) :: Nil) => TermName(name)
          case Apply(_, Nil) => TermName("Fields")
          case _ => 
            c.abort(c.enclosingPosition, "Invalid annotation arguments")
        }

      def extractCompanionObjectParts(cobject: ModuleDef) =
        cobject match {
          case q"$mods object $tname extends { ..$earlydefns } with ..$parents { $self => ..$stats }" =>
            (mods, tname, earlydefns, parents, self, stats)
        }

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
          case q"$mods val $name: $tpt = $rhs" =>
            val annotationName = mods.annotations.collect{
              case Apply(Select((New(Ident(TypeName("BsonProperty"))), _)), Literal(Constant(annName: String)) :: Nil) => Some(annName)
              case Apply(Select((New(Ident(TypeName("bsonField"))), _)), Literal(Constant(annName: String)) :: Nil) => Some(annName)
              case _ => None
            }.flatten.headOption 
            
            val termName = TermName(name.decodedName.toString())
            val bsonName = annotationName.getOrElse(name.decodedName.toString())
            val value = q"${bsonName}"
            val tpe = tq"${bsonName}"

            val definition = ValDef(Modifiers(), termName, tpe, value)

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
          val objectName = extractObjectDestinationName
          val objectFields = q"object $objectName { ..$generatedTerms }"

          val companion =
            q"""
            $classDecl // original class
            object ${className.toTermName} {
              $objectFields
            }
            """
          c.Expr[Any](companion)

        case (classDecl: ClassDef) :: (singleton: ModuleDef) :: Nil =>
          // extract case class and companion object
          val (className, fields) = extractCaseClassParts(classDecl)
          val (mods, tname, earlydefns, parents, self, stats) = extractCompanionObjectParts(singleton)

          // generate the names
          val generatedTerms = generateFieldNames(fields.head)

          // generate Fields object
          val objectName = extractObjectDestinationName
          val objectFields = q"object Fields { ..$generatedTerms }"

          val companion =
            q"""
            $classDecl // original class
            $mods object ${tname.toTermName} extends { ..$earlydefns } with ..$parents { $self =>
              $objectFields
              ..$stats
            }
            """
          c.Expr[Any](companion)
        case _ => c.abort(c.enclosingPosition, "Invalid annottee")
      }
    }
  }

}
