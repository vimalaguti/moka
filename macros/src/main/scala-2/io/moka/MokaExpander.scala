package io.moka

import scala.reflect.macros.whitebox

private[moka] object MokaExpander {

  /** Names read off a generated moka object: the root has path `""`, and every
    * literal is relative to the type the object was generated for.
    */
  sealed trait Names {
    def literal: String
  }
  final case class NLeaf(literal: String) extends Names
  final case class NNode(literal: String, members: Map[String, Names])
      extends Names
}

/** The Scala 2.13 expansion of `@moka`, `@mokaBson` and `@mokaJson`
  * (`specs/specs/2026-10-16-nested-composition-design.md`).
  *
  * The annottee's own fields are read from its untyped parameters. A nested
  * descendable type `N` is *composed*: its names come from the moka object
  * `N`'s companion declares, built in the run that saw `N`'s annotations,
  * because scalac 2.13 does not write zio-bson's `@bsonField` or zio-json's
  * `@jsonField` into classfiles. Where nodes stop is still decided here, by the
  * same rules as direct descent; only the literals come from `N`'s object.
  */
private[moka] final class MokaExpander[C <: whitebox.Context](val c: C) {
  import c.universe._
  import MokaExpander._

  private sealed trait Format {
    def label: String
    def annotation: String
    def placeholder: String
    def defaultObject: String
    def renaming: Set[String]
    def marker: ClassSymbol
  }
  private case object Bson extends Format {
    val label         = "BSON"
    val annotation    = "@moka"
    val placeholder   = "generateFields"
    val defaultObject = "Fields"
    val renaming      = Set("BsonProperty", "bsonField")
    lazy val marker   = c.mirror.staticClass("io.moka.MokaBsonNames")
  }
  private case object Json extends Format {
    val label         = "JSON"
    val annotation    = "@mokaJson"
    val placeholder   = "generateJsonFields"
    val defaultObject = "JsonFields"
    val renaming      = Set("jsonField")
    lazy val marker   = c.mirror.staticClass("io.moka.MokaJsonNames")
  }

  private lazy val neutralMarker = c.mirror.staticClass("io.moka.MokaNames")

  private val allRenaming = Bson.renaming ++ Json.renaming
  private val mokaAnnotations: Map[String, (Format, String)] = Map(
    "moka"     -> ((Bson, "Fields")),
    "mokaBson" -> ((Bson, "BsonFields")),
    "mokaJson" -> ((Json, "JsonFields"))
  )

  private val optOut: Boolean = c.settings.exists { s =>
    s == "moka.descendUnannotated" || s == "moka.descendUnannotated=true"
  }

  /** A nested type that has no moka object to compose, and where it is. */
  private final class Missing(
      val name: String,
      val at: String,
      val generic: Boolean,
      val failed: Boolean,
      val pos: Position
  )

  /** What one generated object depended on. */
  private final class Acc {
    var renamed: Boolean = false
    val warned           = scala.collection.mutable.Set.empty[Symbol]
    val missing = scala.collection.mutable.LinkedHashMap.empty[String, Missing]
    // the annottee's own fields renamed for this format, and for the other one
    val ownRenamed         = scala.collection.mutable.ListBuffer.empty[String]
    val otherRenamed       = scala.collection.mutable.ListBuffer.empty[String]
    var otherPos: Position = NoPosition
  }

  // ---------------------------------------------------------------------------
  // Reading annotations

  /** The simple name of an annotation's type, looking through meta-annotations:
    * `@(BsonProperty @field)("x")` parses as `Annotated(field, BsonProperty)`.
    */
  private def leafTypeName(tree: Tree): Option[String] = tree match {
    case Ident(TypeName(name))     => Some(name)
    case Select(_, TypeName(name)) => Some(name)
    case Annotated(_, arg)         => leafTypeName(arg)
    case _                         => None
  }

  private def findStringConstant(tree: Tree): Option[String] = tree match {
    case Literal(Constant(v: String)) => Some(v)
    case _ => tree.children.view.flatMap(findStringConstant).headOption
  }

  /** Renaming annotations as (simple name, value); an empty value is no
    * annotation at all (spec rule 5).
    */
  private def renamesFromMods(mods: Modifiers): List[(String, String)] =
    mods.annotations
      .flatMap {
        case Apply(Select(New(tpt), _), args) =>
          // the value is read from the arguments only, never from a
          // meta-annotation inside `tpt`
          leafTypeName(tpt)
            .filter(allRenaming)
            .flatMap(n =>
              args.view.flatMap(findStringConstant).headOption.map(v => (n, v))
            )
        case _ => None
      }
      .filter(_._2.nonEmpty)

  private def renamesFromSymbol(sym: Symbol): List[(String, String)] = {
    sym.info // force completion before reading annotations
    sym.annotations
      .flatMap { ann =>
        val name = ann.tree.tpe.typeSymbol.name.decodedName.toString
        if (allRenaming(name)) findStringConstant(ann.tree).map(v => (name, v))
        else None
      }
      .filter(_._2.nonEmpty)
  }

  private def resolveName(
      pos: Position,
      fallback: String,
      renames: List[(String, String)],
      format: Format
  ): String = {
    val matches  = renames.filter(r => format.renaming(r._1))
    val distinct = matches.map(_._2).distinct
    if (distinct.isEmpty) fallback
    else if (distinct.length == 1) distinct.head
    else {
      val formatted =
        matches.map { case (ann, v) => s"@$ann(\"$v\")" }.mkString(", ")
      c.abort(
        pos,
        s"moka: conflicting ${format.label} renaming annotations on field '$fallback': $formatted."
      )
    }
  }

  // ---------------------------------------------------------------------------
  // Shape: the existing descent rules

  private val optionSym   = c.mirror.staticClass("scala.Option")
  private val iterableSym = c.mirror.staticClass("scala.collection.Iterable")
  private val anyValTpe   = c.mirror.staticClass("scala.AnyVal").toType

  /** `Option` and single-element collections are transparent: MongoDB's dot
    * notation is the same whether a sub-document is optional, in an array, or
    * neither. `Map` has two type arguments and is left alone.
    *
    * Returns the element type and whether a collection was crossed on the way
    * to it, which is what decides whether the field gets the array operators.
    */
  private def unwrap(
      t: Type,
      sawCollection: Boolean = false
  ): (Type, Boolean) = {
    val d = t.dealias
    if (d.typeArgs.size == 1 && d.typeSymbol == optionSym)
      unwrap(d.typeArgs.head, sawCollection)
    else if (d.typeArgs.size == 1 && d.baseClasses.contains(iterableSym))
      unwrap(d.typeArgs.head, true)
    else (d, sawCollection)
  }

  /** Case classes are descended into; value classes are not (a value class is
    * stored flattened, so its path is the outer field's path). A symbol from a
    * classfile has no flags until its info is loaded, so `isCaseClass` would be
    * false for a type no other code has touched yet.
    */
  private def isDescendable(t: Type): Boolean = {
    val s = t.dealias.typeSymbol
    s.isClass && { s.info; s.asClass.isCaseClass } && !(t.dealias <:< anyValTpe)
  }

  private def key(t: Type): String = t.typeSymbol.fullName

  private def params(tpe: Type): List[Symbol] =
    tpe.dealias.typeSymbol.asClass.primaryConstructor.asMethod.paramLists.headOption
      .getOrElse(Nil)

  /** A constructor parameter's type as a member of `tpe`: the class's type
    * parameters replaced by `tpe`'s type arguments, so `Box[Inner]`'s
    * `value: T` is an `Inner`. `typeSignatureIn` does not do it: a constructor
    * parameter is owned by the constructor, not by the class.
    */
  private def paramType(p: Symbol, tpe: Type): Type = {
    val d       = tpe.dealias
    val tparams = d.typeSymbol.asClass.typeParams
    val sig     = p.typeSignatureIn(d)
    if (tparams.isEmpty || tparams.length != d.typeArgs.length) sig
    else sig.substituteTypes(tparams, d.typeArgs)
  }

  private def pathOf(prefix: String, name: String): String =
    if (prefix.isEmpty) name
    else if (name.isEmpty) prefix
    else prefix + "." + name

  private def leaf(term: TermName, path: String): Tree =
    ValDef(Modifiers(), term, tq"$path", q"$path")

  /** A member whose literal is `N`'s object's literal `lit` re-prefixed with
    * `base`. When `N`'s object has a stable path, the initializer spells it as
    * `"base." + N.Obj.member`: the typer folds that to the same constant, and
    * the attachment it leaves on the folded literal is where zinc finds the
    * dependency on `N`'s object and the member's name. Without it, a rename in
    * `N` would not recompile this file incrementally. The declared type stays
    * the literal type, so selections still fold at use sites.
    */
  private def composedVal(
      term: TermName,
      base: String,
      lit: String,
      ref: Option[List[String]]
  ): Tree = {
    val path = pathOf(base, lit)
    ref match {
      case Some(r) if base.nonEmpty && lit.nonEmpty =>
        ValDef(Modifiers(), term, tq"$path", q"${base + "."} + ${select(r)}")
      case _ => leaf(term, path)
    }
  }

  private def select(path: List[String]): Tree =
    path.foldLeft(Ident(termNames.ROOTPKG): Tree)((q, n) =>
      Select(q, TermName(n))
    )

  private def nodeObject(term: TermName, path: String, members: List[Tree]) =
    nodeObjectWith(term, path, leaf(TermName("_path"), path), members)

  private def nodeObjectWith(
      term: TermName,
      path: String,
      pathMember: Tree,
      members: List[Tree]
  ) =
    q"object $term extends _root_.io.moka.FieldPath[$path] { ..${pathMember :: members} }"

  // ---------------------------------------------------------------------------
  // Lookup of a nested type's object (spec rule 2)

  private def isMarked(module: Symbol, marker: ClassSymbol): Boolean =
    module.asModule.moduleClass.info.baseClasses.contains(marker)

  private def literalOf(sym: Symbol): Option[String] =
    sym.info.finalResultType match {
      case ct: ConstantType =>
        ct.value.value match {
          case s: String => Some(s)
          case _         => None
        }
      case _ => None
    }

  private def readObject(module: Symbol, root: Boolean): NNode = {
    val decls = module.asModule.moduleClass.info.decls.toList
    val path =
      if (root) ""
      else
        decls
          .find(d => d.isMethod && d.name.decodedName.toString == "_path")
          .flatMap(literalOf)
          .getOrElse("")
    val members = decls.flatMap { d =>
      val name = d.name.decodedName.toString
      if (d.isModule) Some(name -> readObject(d, root = false))
      else if (d.isMethod && d.asMethod.isGetter && name != "_path")
        literalOf(d).map(l => name -> NLeaf(l))
      else None
    }
    NNode(path, members.toMap)
  }

  /** The `_root_`-relative path of a module reachable through packages and
    * objects only. Local and inner companions have none: their outer type is in
    * the same file, so incremental compilation needs no reference.
    */
  private def staticPath(module: Symbol): Option[List[String]] = {
    def loop(s: Symbol, acc: List[String]): Option[List[String]] =
      if (s == c.mirror.RootClass) Some(acc)
      else if (s == c.mirror.EmptyPackageClass || s == NoSymbol) None
      else if (s.isPackageClass || s.isModuleClass)
        loop(s.owner, s.name.decodedName.toString :: acc)
      else None
    loop(module.owner, List(module.name.decodedName.toString))
  }

  /** `N`'s own object for `format`: a module its companion declares, marked
    * with the format's marker or the neutral one. Several identical ones are
    * one; several that differ are an error.
    */
  private def lookup(
      tpe: Type,
      format: Format,
      pos: Position,
      acc: Acc
  ): Option[(NNode, Option[List[String]])] = {
    val cls = tpe.typeSymbol.asClass
    if (cls.typeParams.nonEmpty) None
    else {
      val companion = cls.companion
      if (companion == NoSymbol || !companion.isModule) None
      else {
        val candidates = companion.info.decls.toList.filter { d =>
          d.isModule &&
          (isMarked(d, format.marker) || isMarked(d, neutralMarker))
        }
        val read = candidates.map(m => (m, readObject(m, root = true)))
        read.map(_._2).distinct match {
          case Nil          => None
          case names :: Nil =>
            // prefer a neutral one: its names are the same, and it keeps the
            // outer neutral
            val (chosen, _) = read
              .find(r => isMarked(r._1, neutralMarker))
              .getOrElse(read.head)
            if (!isMarked(chosen, neutralMarker)) acc.renamed = true
            Some(
              (
                names,
                staticPath(companion).map(_ :+ chosen.name.decodedName.toString)
              )
            )
          case _ =>
            c.abort(
              pos,
              s"moka: ambiguous ${format.label} names for nested type ${cls.name}: " +
                s"its companion declares differing objects ${read.map(_._1.name.decodedName.toString).mkString(", ")}. " +
                "Keep one, or make them identical."
            )
        }
      }
    }
  }

  private lazy val notGenerated =
    c.mirror.staticClass("io.moka.FieldsNotGenerated_AddYmacroAnnotations")

  /** `cls` is from this run and carries a moka annotation or placeholder that
    * did not expand: its own expansion failed and reported why.
    */
  private def expansionFailed(cls: ClassSymbol): Boolean =
    cls.pos != NoPosition && {
      cls.info
      val annotated = cls.annotations.exists(a =>
        mokaAnnotations.contains(
          a.tree.tpe.typeSymbol.name.decodedName.toString
        )
      )
      val companion = cls.companion
      annotated || (companion.isModule && companion.info.decls.exists { d =>
        d.isMethod && d.info.finalResultType.typeSymbol == notGenerated
      })
    }

  /** Records `tpe`, which has no object for `format`, and every descendable
    * type below it that has none either: `tpe` will need its own object, and so
    * will they, so one error can name them all.
    */
  private def recordMissing(
      tpe: Type,
      path: String,
      seen: Set[String],
      format: Format,
      pos: Position,
      acc: Acc
  ): Unit = if (!acc.missing.contains(key(tpe))) {
    val cls    = tpe.typeSymbol.asClass
    val failed = expansionFailed(cls)
    acc.missing(key(tpe)) = new Missing(
      cls.name.decodedName.toString,
      path,
      cls.typeParams.nonEmpty,
      failed,
      pos
    )
    // a failed expansion already reported what is missing below it
    if (!failed) params(tpe).foreach { p =>
      val (pt, _) = unwrap(paramType(p, tpe))
      if (
        isDescendable(pt) && !seen(key(pt)) && lookup(
          pt,
          format,
          pos,
          acc
        ).isEmpty
      )
        recordMissing(
          pt,
          pathOf(path, p.name.decodedName.toString),
          seen + key(pt),
          format,
          pos,
          acc
        )
    }
  }

  private def reportMissing(format: Format, acc: Acc): Unit =
    if (acc.missing.nonEmpty) {
      val all  = acc.missing.values.toList
      val todo = all.filterNot(_.failed)
      val lines = all.map { m =>
        val fix =
          if (m.failed)
            s"it carries a moka annotation, but its own expansion failed: fix the error reported at ${m.name}"
          else if (m.generic)
            "it is generic, so it cannot carry one: only the opt-out reaches it"
          else s"add ${format.annotation}"
        s"\n  - ${m.name} (at ${m.at}): $fix"
      }
      val tail =
        if (todo.isEmpty) ""
        else
          s"\nIn cross-compiled sources, also add `val ${format.defaultObject} = ${format.placeholder}[T]` to each " +
            "companion. Objects compiled by moka 0.1.x carry no marker: recompile them. Or compile with " +
            "-Xmacro-settings:moka.descendUnannotated to read their annotations anyway."
      c.abort(
        all.head.pos,
        s"moka: nested types without a ${format.label} moka object (on Scala 2.13 their renaming annotations " +
          "may be lost from classfiles):" + lines.mkString + tail
      )
    }

  // ---------------------------------------------------------------------------
  // Descent

  /** A nested type where no enclosing object has been composed: compose its own
    * object, or (opt-out) read its annotations.
    */
  private def descend(
      term: TermName,
      tpe: Type,
      path: String,
      seen: Set[String],
      isArray: Boolean,
      format: Format,
      pos: Position,
      acc: Acc
  ): Tree =
    lookup(tpe, format, pos, acc) match {
      case Some((names, ref)) =>
        composed(term, tpe, path, names, ref, seen, isArray, root = true, pos)
      case None if optOut =>
        val sym = tpe.typeSymbol
        if (!acc.warned(sym)) {
          acc.warned += sym
          c.warning(
            pos,
            s"moka: reading the annotations of nested type ${sym.name.decodedName} directly " +
              "(-Xmacro-settings:moka.descendUnannotated): renames scalac 2.13 dropped from its classfile " +
              "(zio-bson @bsonField, zio-json @jsonField) are not seen. Give it its own moka object instead."
          )
        }
        acc.renamed = true // never neutral under the opt-out
        direct(term, tpe, path, seen, isArray, format, pos, acc)
      case None =>
        recordMissing(tpe, path, seen, format, pos, acc)
        leaf(term, path) // never emitted: reportMissing aborts
    }

  /** Shape from the outer, names from `N`'s object: `names` is a node of it (at
    * stable path `ref`, if any), every literal relative to `base`, the path `N`
    * is composed at.
    */
  private def composed(
      term: TermName,
      tpe: Type,
      base: String,
      names: NNode,
      ref: Option[List[String]],
      seen: Set[String],
      isArray: Boolean,
      root: Boolean,
      pos: Position
  ): Tree = {
    val n = tpe.typeSymbol.name.decodedName.toString
    def mismatch(member: String) =
      c.abort(
        pos,
        s"moka: the moka object of $n does not match its type at '$member': recompile $n with this moka version."
      )
    val members = params(tpe).map { p =>
      val name         = p.name.decodedName.toString
      val (pt, arr)    = unwrap(paramType(p, tpe))
      val found: Names = names.members.getOrElse(name, mismatch(name))
      if (isDescendable(pt) && !seen(key(pt))) found match {
        case node: NNode =>
          composed(
            TermName(name),
            pt,
            base,
            node,
            ref.map(_ :+ name),
            seen + key(pt),
            arr,
            root = false,
            pos
          )
        case _ => mismatch(name)
      }
      else {
        val litRef = found match {
          case _: NNode => ref.map(_ ::: List(name, "_path"))
          case _        => ref.map(_ :+ name)
        }
        composedVal(TermName(name), base, found.literal, litRef)
      }
    }
    val path = pathOf(base, names.literal)
    val arrayOps =
      if (!isArray) Nil
      else if (root)
        // an outer collection over N: compose N again under the operators
        List("_matched" -> ".$", "_all" -> ".$[]").map { case (op, suffix) =>
          composed(
            TermName(op),
            tpe,
            path + suffix,
            names,
            ref,
            seen,
            isArray = false,
            root = true,
            pos
          )
        }
      else
        List("_matched", "_all").map { op =>
          names.members.get(op) match {
            case Some(node: NNode) =>
              composed(
                TermName(op),
                tpe,
                base,
                node,
                ref.map(_ :+ op),
                seen,
                isArray = false,
                root = false,
                pos
              )
            case _ => mismatch(op)
          }
        }
    val pathMember =
      if (root) leaf(TermName("_path"), path)
      else
        composedVal(
          TermName("_path"),
          base,
          names.literal,
          ref.map(_ :+ "_path")
        )
    nodeObjectWith(term, path, pathMember, members ::: arrayOps)
  }

  /** Reads a nested type's annotations, under the opt-out only. */
  private def direct(
      term: TermName,
      tpe: Type,
      path: String,
      seen: Set[String],
      isArray: Boolean,
      format: Format,
      pos: Position,
      acc: Acc
  ): Tree = {
    val members = params(tpe).map { p =>
      val fieldName = p.name.decodedName.toString
      val ppos      = if (p.pos != NoPosition) p.pos else pos
      val ppath =
        pathOf(path, resolveName(ppos, fieldName, renamesFromSymbol(p), format))
      val (pt, arr) = unwrap(paramType(p, tpe))
      if (isDescendable(pt) && !seen(key(pt)))
        descend(
          TermName(fieldName),
          pt,
          ppath,
          seen + key(pt),
          arr,
          format,
          pos,
          acc
        )
      else leaf(TermName(fieldName), ppath)
    }
    val arrayOps =
      if (!isArray) Nil
      else
        List("_matched" -> ".$", "_all" -> ".$[]").map { case (op, suffix) =>
          direct(
            TermName(op),
            tpe,
            path + suffix,
            seen,
            isArray = false,
            format,
            pos,
            acc
          )
        }
    nodeObject(term, path, members ::: arrayOps)
  }

  /** Simple names of the types and objects declared next to the annottee, in
    * the body of its enclosing class or object. `c.typecheck` cannot see them
    * (the annotation macro runs before the typer), so a field type naming one
    * would silently resolve to whatever same-named type is visible outside.
    */
  @scala.annotation.nowarn("cat=deprecation")
  private lazy val siblingNames: Set[String] = {
    val body = c.enclosingClass match {
      case ClassDef(_, _, _, impl) => impl.body
      case ModuleDef(_, _, impl)   => impl.body
      case _                       => Nil
    }
    body.collect {
      case ClassDef(_, n, _, _) => n.decodedName.toString
      case TypeDef(_, n, _, _)  => n.decodedName.toString
      case ModuleDef(_, n, _)   => n.decodedName.toString
    }.toSet
  }

  /** The members of one generated root object, from the annottee's untyped
    * parameters.
    */
  private def fieldNames(
      className: TypeName,
      fields: List[ValDef],
      format: Format,
      acc: Acc
  ): List[Tree] = {
    val selfName = className.decodedName.toString
    fields.map { vd =>
      val fieldName = vd.name.decodedName.toString
      val renames   = renamesFromMods(vd.mods)
      if (renames.nonEmpty) acc.renamed = true
      if (renames.exists(r => format.renaming(r._1)))
        acc.ownRenamed += fieldName
      else if (renames.nonEmpty) {
        if (acc.otherRenamed.isEmpty) acc.otherPos = vd.pos
        acc.otherRenamed += fieldName
      }
      val path = resolveName(vd.pos, fieldName, renames, format)
      val term = TermName(fieldName)
      val tpt  = vd.tpt
      // Typechecking a type that mentions the annottee would re-enter this very
      // annotation expansion, so a self-reference is recognised syntactically
      // and terminates as a leaf.
      val mentionsSelf = tpt.exists {
        case Ident(n)     => n.decodedName.toString == selfName
        case Select(_, n) => n.decodedName.toString == selfName
        case _            => false
      }
      // a sibling of the annottee shadows any same-named type outside, but
      // typechecking would find only the outside one
      val mentionsSibling = tpt.exists {
        case Ident(n) =>
          val s = n.decodedName.toString
          s != selfName && siblingNames(s)
        case _ => false
      }
      if (mentionsSelf) leaf(term, path)
      else {
        val resolved =
          if (mentionsSibling) EmptyTree
          else c.typecheck(tpt.duplicate, c.TYPEmode, silent = true)
        if (resolved.isEmpty)
          c.abort(
            vd.pos,
            s"moka: cannot resolve type '$tpt' of field '$fieldName' while expanding @moka on $selfName. " +
              "On Scala 2 the annotation macro runs before the typer, so a type declared as a member of the " +
              s"same enclosing object or class as the annotated case class is invisible to it. Move '$tpt' to " +
              "package level or into another file."
          )
        val (ft, isArray) = unwrap(resolved.tpe)
        if (isDescendable(ft))
          descend(term, ft, path, Set(key(ft)), isArray, format, vd.pos, acc)
        else leaf(term, path)
      }
    }
  }

  /** One generated root object: its members and its marker (spec rule 1). */
  private def rootObject(
      name: TermName,
      className: TypeName,
      fields: List[ValDef],
      format: Format,
      formats: Set[Format]
  ): Tree = {
    val acc   = new Acc
    val terms = fieldNames(className, fields, format, acc)
    reportMissing(format, acc)
    val other = if (format == Bson) Json else Bson
    // generating the other format's object too says the ignoring is deliberate
    if (
      acc.ownRenamed.isEmpty && acc.otherRenamed.nonEmpty && !formats(other)
    ) {
      c.warning(
        acc.otherPos,
        s"moka: $className's fields are renamed only by ${other.label} annotations " +
          s"(${acc.otherRenamed.mkString(", ")}), which its ${format.label} object $name ignores: its names " +
          s"are the Scala field names. If the documents use the ${other.label} names, generate them with " +
          s"${other.placeholder} or ${other.annotation}."
      )
    }
    val marker = if (acc.renamed) format.marker else neutralMarker
    q"object $name extends ${marker} { ..$terms }"
  }

  // ---------------------------------------------------------------------------
  // Companion rewriting (spec rule 6)

  private def annotationParts(tree: Tree): Option[(String, List[Tree])] =
    tree match {
      case Apply(Select(New(tpt), _), args) =>
        leafTypeName(tpt).filter(mokaAnnotations.contains).map(n => (n, args))
      case _ => None
    }

  private def objectName(args: List[Tree], default: String): String =
    args match {
      case Nil                                 => default
      case Literal(Constant(n: String)) :: Nil => n
      case NamedArg(
            Ident(TermName("name")),
            Literal(Constant(n: String))
          ) :: Nil =>
        n
      case Assign(
            Ident(TermName("name")),
            Literal(Constant(n: String))
          ) :: Nil =>
        n
      case _ =>
        c.abort(
          c.enclosingPosition,
          "moka: invalid annotation arguments: expected a string literal object name."
        )
    }

  private def placeholderFormat(rhs: Tree): Option[Format] = rhs match {
    case q"$_.generateFields[$_]"     => Some(Bson)
    case q"generateFields[$_]"        => Some(Bson)
    case q"$_.generateBsonFields[$_]" => Some(Bson)
    case q"generateBsonFields[$_]"    => Some(Bson)
    case q"$_.generateJsonFields[$_]" => Some(Json)
    case q"generateJsonFields[$_]"    => Some(Json)
    case _                            => None
  }

  def expand(annotation: String, annottees: Seq[Tree]): Tree = {
    val (classDecl, companion) = annottees.toList match {
      case (cd: ClassDef) :: Nil                    => (cd, None)
      case (cd: ClassDef) :: (md: ModuleDef) :: Nil => (cd, Some(md))
      case _ => c.abort(c.enclosingPosition, "moka: invalid annottee")
    }
    val ClassDef(classMods, className, classTparams, classImpl) = classDecl
    if (!classMods.hasFlag(Flag.CASE))
      c.abort(
        c.enclosingPosition,
        s"moka: class $className is not a case class."
      )
    if (classTparams.nonEmpty)
      c.abort(
        c.enclosingPosition,
        s"moka: $className is generic: on Scala 2.13 generic case classes cannot carry moka annotations, " +
          "since an outer type could not compose its names from them. Remove the annotation; an outer type can " +
          "still reach it with -Xmacro-settings:moka.descendUnannotated."
      )
    val firstParams: List[ValDef] = classDecl match {
      case q"$_ class $_[..$_] $_(...$paramss) extends ..$_ { $_ => ..$_ }" =>
        paramss.headOption.getOrElse(Nil)
      case _ => c.abort(c.enclosingPosition, "moka: invalid class " + className)
    }

    // The whole stack is handled here: the annotation expanding now first, then
    // the moka annotations still on the class, which are removed so they never
    // expand on this output.
    val current = c.prefix.tree match {
      case Apply(_, args) => (annotation, args)
      case _              => (annotation, Nil)
    }
    val (others, kept) =
      classMods.annotations.partition(annotationParts(_).isDefined)
    val stack = current :: others.flatMap(annotationParts)
    val strippedClass = ClassDef(
      Modifiers(classMods.flags, classMods.privateWithin, kept),
      className,
      classDecl.tparams,
      classImpl
    )

    val (cMods, cParents, cSelf, cStats) = companion match {
      case Some(q"$mods object $_ extends ..$parents { $self => ..$stats }") =>
        (mods, parents, self, stats)
      case _ => (Modifiers(), List(tq"_root_.scala.AnyRef"), noSelfType, Nil)
    }

    val placeholders = cStats.collect {
      case q"$_ val $name: $_ = $rhs" if placeholderFormat(rhs).isDefined =>
        name.decodedName.toString -> placeholderFormat(rhs).get
    }
    val generated: List[(String, Format)] =
      if (placeholders.nonEmpty) placeholders
      else
        stack.map { case (ann, args) =>
          val (format, default) = mokaAnnotations(ann)
          objectName(args, default) -> format
        }

    val duplicates =
      generated.groupBy(_._1).collect { case (n, l) if l.size > 1 => n }
    if (duplicates.nonEmpty)
      c.abort(
        c.enclosingPosition,
        s"moka: the stacked annotations on $className generate ${duplicates.mkString(", ")} more than once."
      )

    val generatedNames = generated.map(_._1).toSet
    cStats.foreach {
      case q"$_ val $name: $_ = $rhs" if placeholderFormat(rhs).isDefined => ()
      case d: MemberDef
          if d.name.isTermName && generatedNames(d.name.decodedName.toString) =>
        c.abort(
          d.pos,
          s"moka: the companion of $className already defines ${d.name.decodedName}, which moka generates. " +
            "Rename or remove it, or give the generated object another name."
        )
      case _ => ()
    }

    val formats = generated.map(_._2).toSet
    def build(name: String, format: Format) =
      rootObject(TermName(name), className, firstParams, format, formats)

    val newStats =
      if (placeholders.nonEmpty)
        cStats.map {
          case q"$_ val $name: $_ = $rhs" if placeholderFormat(rhs).isDefined =>
            build(name.decodedName.toString, placeholderFormat(rhs).get)
          case other => other
        }
      else generated.map { case (n, f) => build(n, f) } ++ cStats

    q"""
    $strippedClass
    $cMods object ${className.toTermName} extends ..$cParents { $cSelf =>
      ..$newStats
    }
    """
  }
}
