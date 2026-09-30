package io

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

package object moka {

  @compileTimeOnly(
    "io.moka.generateFields is a placeholder that @moka rewrites. If the case class already carries @moka, the annotation did not expand: add scalacOptions += \"-Ymacro-annotations\" (Scala 2.13)."
  )
  def generateFields[T]: FieldsNotGenerated_AddYmacroAnnotations = ???

  @compileTimeOnly(
    "io.moka.generateBsonFields is a placeholder that @moka / @mokaBson rewrites. If the case class already carries @mokaBson, the annotation did not expand: add scalacOptions += \"-Ymacro-annotations\" (Scala 2.13)."
  )
  def generateBsonFields[T]: FieldsNotGenerated_AddYmacroAnnotations = ???

  @compileTimeOnly(
    "io.moka.generateJsonFields is a placeholder that @moka / @mokaJson rewrites. If the case class already carries @mokaJson, the annotation did not expand: add scalacOptions += \"-Ymacro-annotations\" (Scala 2.13)."
  )
  def generateJsonFields[T]: FieldsNotGenerated_AddYmacroAnnotations = ???
}

package moka {

  /** Declared return type of the [[io.moka.generateFields]] placeholder, and
    * the only thing a caller can see if `@moka` never ran. Selecting a field on
    * it fails in the typer, which happens *before* refchecks reports
    * `@compileTimeOnly`, so the type's own name has to carry the diagnostic —
    * otherwise the user's first error is an unexplained "not a member of Unit".
    */
  sealed trait FieldsNotGenerated_AddYmacroAnnotations

  @compileTimeOnly(
    "@moka was not expanded. On Scala 2.13 macro annotations need a compiler flag: add scalacOptions += \"-Ymacro-annotations\"."
  )
  class moka(name: String = "Fields") extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro mokaMacro.implAll
  }

  @compileTimeOnly(
    "@mokaBson was not expanded. On Scala 2.13 macro annotations need a compiler flag: add scalacOptions += \"-Ymacro-annotations\"."
  )
  class mokaBson(name: String = "BsonFields") extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro mokaMacro.implBson
  }

  @compileTimeOnly(
    "@mokaJson was not expanded. On Scala 2.13 macro annotations need a compiler flag: add scalacOptions += \"-Ymacro-annotations\"."
  )
  class mokaJson(name: String = "JsonFields") extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro mokaMacro.implJson
  }

  private[moka] sealed trait RenamingMode
  private[moka] case object BsonOnlyMode extends RenamingMode
  private[moka] case object JsonOnlyMode extends RenamingMode

  object mokaMacro {
    def implAll(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] =
      impl(c, BsonOnlyMode, "Fields")(annottees: _*)

    def implBson(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] =
      impl(c, BsonOnlyMode, "BsonFields")(annottees: _*)

    def implJson(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] =
      impl(c, JsonOnlyMode, "JsonFields")(annottees: _*)

    def impl(
        c: whitebox.Context,
        defaultMode: RenamingMode,
        defaultName: String
    )(
        annottees: c.Expr[Any]*
    ): c.Expr[Any] = {
      import c.universe._

      def extractObjectDestinationName: TermName =
        c.prefix.tree match {
          case Apply(_, Literal(Constant(name: String)) :: Nil) =>
            TermName(name)
          case Apply(_, Nil) => TermName(defaultName)
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
      val jsonAnnotations = Set("jsonField")

      def filterByMode(
          annotations: List[(String, String)],
          mode: RenamingMode
      ): List[(String, String)] =
        mode match {
          case BsonOnlyMode =>
            annotations.filter(a => bsonAnnotations.contains(a._1))
          case JsonOnlyMode =>
            annotations.filter(a => jsonAnnotations.contains(a._1))
        }

      def resolveName(
          pos: Position,
          fallback: String,
          annotations: List[(String, String)],
          mode: RenamingMode
      ): String = {
        val matches        = filterByMode(annotations, mode)
        val distinctValues = matches.map(_._2).distinct
        if (distinctValues.isEmpty) fallback
        else if (distinctValues.length == 1) distinctValues.head
        else {
          val formatted = matches
            .map { case (ann, v) => s"@$ann(\"$v\")" }
            .mkString(", ")
          val target = mode match {
            case BsonOnlyMode => "BSON"
            case JsonOnlyMode => "JSON"
          }
          c.abort(
            pos,
            s"moka: conflicting $target renaming annotations on field '$fallback': $formatted."
          )
        }
      }

      def leafTypeName(tree: Tree): Option[String] = tree match {
        case Ident(TypeName(name))     => Some(name)
        case Select(_, TypeName(name)) => Some(name)
        case _                         => None
      }

      def findStringConstant(tree: Tree): Option[String] = tree match {
        case Literal(Constant(v: String)) => Some(v)
        case _ => tree.children.view.flatMap(findStringConstant).headOption
      }

      /** Bson/json name read off the annottee's own params, which are still
        * untyped.
        */
      def bsonNameFromMods(
          mods: Modifiers,
          fallback: String,
          pos: Position,
          mode: RenamingMode
      ): String = {
        val extracted = mods.annotations.flatMap {
          case tree @ Apply(Select(New(tpt), _), _)
              if leafTypeName(tpt).isDefined =>
            val name = leafTypeName(tpt).get
            findStringConstant(tree).map(v => (name, v))
          case _ => None
        }
        resolveName(pos, fallback, extracted, mode)
      }

      /** Bson/json name read off a nested type's constructor param, which is
        * typed.
        */
      def bsonNameFromSymbol(
          sym: Symbol,
          fallback: String,
          mode: RenamingMode
      ): String = {
        sym.info // force completion before reading annotations
        val extracted = sym.annotations.flatMap { ann =>
          val name = ann.tree.tpe.typeSymbol.name.decodedName.toString
          findStringConstant(ann.tree).map(v => (name, v))
        }
        val pos =
          if (sym.pos != c.universe.NoPosition) sym.pos
          else c.enclosingPosition
        resolveName(pos, fallback, extracted, mode)
      }

      /** Case classes are descended into; value classes are not (a value class
        * is stored flattened, so its path is the outer field's path).
        */
      val optionSym   = typeOf[Option[Any]].typeSymbol
      val iterableTpe = typeOf[Iterable[Any]]

      /** `Option` and single-element collections are transparent: MongoDB's dot
        * notation is the same whether a sub-document is optional, in an array,
        * or neither. `Map` has two type arguments and is left alone.
        *
        * Returns the element type and whether a collection was crossed on the
        * way to it, which is what decides whether the field gets the array
        * operators.
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
          isArray: Boolean,
          mode: RenamingMode
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
              node(
                TermName("_matched"),
                tpe,
                path + ".$",
                seen,
                isArray = false,
                mode = mode
              ),
              node(
                TermName("_all"),
                tpe,
                path + ".$[]",
                seen,
                isArray = false,
                mode = mode
              )
            )
          else Nil
        val members =
          pathMember :: (membersOf(tpe, path, seen, mode) ::: arrayOps)
        q"object $term extends _root_.io.moka.FieldPath[$pathType] { ..$members }"
      }

      def membersOf(
          tpe: Type,
          prefix: String,
          seen: Set[String],
          mode: RenamingMode
      ): List[Tree] = {
        val cls = tpe.dealias.typeSymbol.asClass
        val params =
          cls.primaryConstructor.asMethod.paramLists.headOption.getOrElse(Nil)
        params.map { p =>
          val fieldName = p.name.decodedName.toString
          val path      = pathOf(prefix, bsonNameFromSymbol(p, fieldName, mode))
          val (fieldTpe, isArray) = unwrap(p.typeSignatureIn(tpe.dealias))
          val key                 = fieldTpe.typeSymbol.fullName
          if (isDescendable(fieldTpe) && !seen.contains(key))
            node(TermName(fieldName), fieldTpe, path, seen + key, isArray, mode)
          else leaf(TermName(fieldName), path)
        }
      }

      def generateFieldNames(
          className: TypeName,
          terms: List[ValDef],
          mode: RenamingMode
      ): List[Tree] = {
        val selfName = className.decodedName.toString
        terms.map {
          case vd @ q"$mods val $name: $tpt = $rhs" =>
            val fieldName = name.decodedName.toString
            val path      = bsonNameFromMods(mods, fieldName, vd.pos, mode)
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
              val resolved =
                c.typecheck(tpt.duplicate, c.TYPEmode, silent = true)
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
                node(term, ft, path, Set(ft.typeSymbol.fullName), isArray, mode)
              else leaf(term, path)
            }
          case term =>
            c.abort(c.enclosingPosition, "Invalid field: " + term)
        }
      }

      def placeholderMode(rhs: Tree): Option[RenamingMode] = rhs match {
        case q"$_.generateFields[$_]"     => Some(BsonOnlyMode)
        case q"generateFields[$_]"        => Some(BsonOnlyMode)
        case q"$_.generateBsonFields[$_]" => Some(BsonOnlyMode)
        case q"generateBsonFields[$_]"    => Some(BsonOnlyMode)
        case q"$_.generateJsonFields[$_]" => Some(JsonOnlyMode)
        case q"generateJsonFields[$_]"    => Some(JsonOnlyMode)
        case _                            => None
      }

      annottees.map(_.tree).toList match {
        case (classDecl: ClassDef) :: Nil =>
          val (className, fields) = extractCaseClassParts(classDecl)

          // generate the names
          val generatedTerms =
            generateFieldNames(className, fields.head, defaultMode)

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
          val (className, fields) = extractCaseClassParts(classDecl)
          val (mods, tname, parents, self, stats) = extractCompanionObjectParts(
            singleton
          )
          val objectName = extractObjectDestinationName

          // replace placeholder vals (val X = generateFields[T]) with the
          // generated object, so cross-compiled sources can share definitions
          // with the Scala 3 inline macro
          var replacedPlaceholder = false
          val updatedStats = stats.map {
            case q"$_ val $name: $_ = $rhs" if placeholderMode(rhs).isDefined =>
              replacedPlaceholder = true
              val mode  = placeholderMode(rhs).get
              val terms = generateFieldNames(className, fields.head, mode)
              q"object $name { ..$terms }"
            case other => other
          }
          val alreadyDefined = stats.exists {
            case q"$_ object $name extends ..$_ { $_ => ..$_ }" =>
              name.decodedName.toString == objectName.decodedName.toString
            case _ => false
          }
          val newStats =
            if (replacedPlaceholder) updatedStats
            else if (alreadyDefined) stats
            else {
              val terms =
                generateFieldNames(className, fields.head, defaultMode)
              q"object $objectName { ..$terms }" +: stats
            }

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
