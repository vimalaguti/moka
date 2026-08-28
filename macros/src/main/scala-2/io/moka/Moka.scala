package io

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

package object moka {

  @compileTimeOnly(
    "io.moka.generateFields is a placeholder rewritten by @moka: annotate the case class with @moka"
  )
  def generateFields[T]: Unit = ()
}

package moka {

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
          case Apply(_, Nil)                                    => TermName("Fields")
          case _ =>
            c.abort(c.enclosingPosition, "Invalid annotation arguments")
        }

      def extractCompanionObjectParts(cobject: ModuleDef) =
        cobject match {
          case q"$mods object $tname extends ..$parents { $self => ..$stats }" =>
            (mods, tname, parents, self, stats)
        }

      def extractCaseClassParts(
          classDecl: ClassDef
      ): (TypeName, List[List[ValDef]]) =
        classDecl match {
          case q"$mods class $tpname[..$tparams] $ctorMods(...$paramss) extends ..$parents { $self => ..$stats }" =>
            if (mods.hasFlag(Flag.CASE)) (tpname, paramss)
            else
              c.abort(
                c.enclosingPosition,
                "Class is not a case class: " + tpname
              )
          case _ => c.abort(c.enclosingPosition, "Invalid class " + classDecl)
        }

      val bsonAnnotations = Set("BsonProperty", "bsonField")

      /** Bson name read off the annottee's own params, which are still untyped. */
      def bsonNameFromMods(mods: Modifiers, fallback: String): String =
        mods.annotations.collect {
          case Apply(Select(New(Ident(TypeName(ann))), _), Literal(Constant(v: String)) :: Nil)
              if bsonAnnotations.contains(ann) =>
            v
        }.headOption.getOrElse(fallback)

      /** Bson name read off a nested type's constructor param, which is typed. */
      def bsonNameFromSymbol(sym: Symbol, fallback: String): String = {
        sym.info // force completion before reading annotations
        sym.annotations
          .collectFirst {
            case ann
                if bsonAnnotations.contains(
                  ann.tree.tpe.typeSymbol.name.decodedName.toString
                ) =>
              ann.tree.children.collectFirst { case Literal(Constant(v: String)) => v }
          }
          .flatten
          .getOrElse(fallback)
      }

      /** Case classes are descended into; value classes are not (a value class is
        * stored flattened, so its path is the outer field's path).
        */
      val optionSym   = typeOf[Option[Any]].typeSymbol
      val iterableTpe = typeOf[Iterable[Any]]

      /** `Option` and single-element collections are transparent: MongoDB's dot
        * notation is the same whether a sub-document is optional, in an array, or
        * neither. `Map` has two type arguments and is left alone.
        *
        * Returns the element type and whether a collection was crossed on the way
        * to it, which is what decides whether the field gets the array operators.
        */
      def unwrap(t: Type, sawCollection: Boolean = false): (Type, Boolean) = {
        val d = t.dealias
        if (d.typeArgs.size == 1 && d.typeSymbol == optionSym)
          unwrap(d.typeArgs.head, sawCollection)
        else if (d.typeArgs.size == 1 && d <:< iterableTpe)
          unwrap(d.typeArgs.head, true)
        else (d, sawCollection)
      }

      def isDescendable(t: Type): Boolean = {
        val s = t.dealias.typeSymbol
        s.isClass && s.asClass.isCaseClass && !(t.dealias <:< typeOf[AnyVal])
      }

      def pathOf(prefix: String, name: String): String =
        if (prefix.isEmpty) name else prefix + "." + name

      def leaf(term: TermName, path: String): Tree =
        ValDef(Modifiers(), term, tq"$path", q"$path")

      def node(
          term: TermName,
          tpe: Type,
          path: String,
          seen: Set[String],
          isArray: Boolean
      ): Tree = {
        val pathType  = tq"$path"
        val pathValue = q"$path"
        val pathMember =
          ValDef(Modifiers(), TermName("_path"), pathType, pathValue)
        // MongoDB's array operators. Neither is itself an array, so they do not
        // nest further.
        val arrayOps =
          if (isArray)
            List(
              node(TermName("_matched"), tpe, path + ".$", seen, isArray = false),
              node(TermName("_all"), tpe, path + ".$[]", seen, isArray = false)
            )
          else Nil
        val members = pathMember :: (membersOf(tpe, path, seen) ::: arrayOps)
        q"object $term extends _root_.io.moka.FieldPath[$pathType] { ..$members }"
      }

      def membersOf(tpe: Type, prefix: String, seen: Set[String]): List[Tree] = {
        val cls = tpe.dealias.typeSymbol.asClass
        val params =
          cls.primaryConstructor.asMethod.paramLists.headOption.getOrElse(Nil)
        params.map { p =>
          val fieldName = p.name.decodedName.toString
          val path      = pathOf(prefix, bsonNameFromSymbol(p, fieldName))
          val (fieldTpe, isArray) = unwrap(p.typeSignatureIn(tpe.dealias))
          val key                 = fieldTpe.typeSymbol.fullName
          if (isDescendable(fieldTpe) && !seen.contains(key))
            node(TermName(fieldName), fieldTpe, path, seen + key, isArray)
          else leaf(TermName(fieldName), path)
        }
      }

      def generateFieldNames(className: TypeName, terms: List[ValDef]): List[Tree] = {
        val selfName = className.decodedName.toString
        terms.map {
          case vd @ q"$mods val $name: $tpt = $rhs" =>
            val fieldName = name.decodedName.toString
            val path      = bsonNameFromMods(mods, fieldName)
            val term      = TermName(fieldName)
            // Typechecking a type that mentions the annottee would re-enter this
            // very annotation expansion, so a self-reference is recognised
            // syntactically and terminates as a leaf.
            val mentionsSelf = tpt.exists {
              case Ident(n)     => n.decodedName.toString == selfName
              case Select(_, n) => n.decodedName.toString == selfName
              case _            => false
            }
            if (mentionsSelf) leaf(term, path)
            else {
              val resolved = c.typecheck(tpt.duplicate, c.TYPEmode, silent = true)
              if (resolved.isEmpty)
                c.abort(
                  vd.pos,
                  s"moka cannot resolve type '$tpt' of field '$fieldName' while expanding @moka on $selfName. " +
                    "On Scala 2 the annotation macro runs before the typer, so a type declared as a member of the " +
                    s"same enclosing object or class as the annotated case class is invisible to it. Move '$tpt' to " +
                    "package level or into another file."
                )
              val (ft, isArray) = unwrap(resolved.tpe)
              if (isDescendable(ft))
                node(term, ft, path, Set(ft.typeSymbol.fullName), isArray)
              else leaf(term, path)
            }
          case term =>
            c.abort(c.enclosingPosition, "Invalid field: " + term)
        }
      }

      def isGenerateFieldsCall(rhs: Tree): Boolean = rhs match {
        case q"$_.generateFields[$_]" => true
        case q"generateFields[$_]"    => true
        case _                        => false
      }

      annottees.map(_.tree).toList match {
        case (classDecl: ClassDef) :: Nil =>
          val (className, fields) = extractCaseClassParts(classDecl)

          // generate the names
          val generatedTerms = generateFieldNames(className, fields.head)

          // generate Fields object
          val objectName   = extractObjectDestinationName
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
          val (className, fields)                  = extractCaseClassParts(classDecl)
          val (mods, tname, parents, self, stats)  = extractCompanionObjectParts(singleton)

          // generate the names
          val generatedTerms = generateFieldNames(className, fields.head)
          val objectName     = extractObjectDestinationName

          // replace placeholder vals (val X = generateFields[T]) with the
          // generated object, so cross-compiled sources can share definitions
          // with the Scala 3 inline macro
          var replacedPlaceholder = false
          val updatedStats = stats.map {
            case q"$_ val $name: $_ = $rhs" if isGenerateFieldsCall(rhs) =>
              replacedPlaceholder = true
              q"object $name { ..$generatedTerms }"
            case other => other
          }
          val newStats =
            if (replacedPlaceholder) updatedStats
            else q"object $objectName { ..$generatedTerms }" +: stats

          val companion =
            q"""
            $classDecl // original class
            $mods object ${tname.toTermName} extends ..$parents { $self =>
              ..$newStats
            }
            """
          c.Expr[Any](companion)
        case _ => c.abort(c.enclosingPosition, "Invalid annottee")
      }
    }
  }
}
