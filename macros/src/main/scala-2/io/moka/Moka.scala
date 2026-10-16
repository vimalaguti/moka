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

  object mokaMacro {
    def implAll(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] =
      expand(c, "moka")(annottees: _*)

    def implBson(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] =
      expand(c, "mokaBson")(annottees: _*)

    def implJson(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] =
      expand(c, "mokaJson")(annottees: _*)

    private def expand(c: whitebox.Context, annotation: String)(
        annottees: c.Expr[Any]*
    ): c.Expr[Any] = {
      val expander = new MokaExpander[c.type](c)
      c.Expr[Any](expander.expand(annotation, annottees.map(_.tree)))
    }
  }
}
