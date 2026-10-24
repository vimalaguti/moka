package io.moka

import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.io.VirtualDirectory
import scala.tools.nsc.{Global, Settings}
import scala.tools.nsc.reporters.StoreReporter

/** Compiles sources with scalac in-process, as the `examples` main run would,
  * and returns every diagnostic. munit's `compileErrors` returns only the first
  * error of one snippet, so it cannot show warnings or a second macro abort.
  */
object CompileHarness {
  final case class Diagnostic(severity: String, line: Int, msg: String)

  private lazy val classpath: String =
    scala.io.Source
      .fromResource("moka-compile-classpath.txt")
      .mkString

  def compile(sources: (String, String)*): List[Diagnostic] =
    compileWith("-Ymacro-annotations -Xsource:3")(sources: _*)

  def compileWith(
      options: String
  )(sources: (String, String)*): List[Diagnostic] = {
    val settings = new Settings()
    settings.classpath.value = classpath
    settings.processArgumentString(options)
    settings.outputDirs.setSingleOutput(new VirtualDirectory("(memory)", None))
    val reporter = new StoreReporter(settings)
    val global   = new Global(settings, reporter)
    val run      = new global.Run
    run.compileSources(sources.toList.map { case (name, code) =>
      new BatchSourceFile(name, code)
    })
    reporter.infos.toList.map(i =>
      Diagnostic(i.severity.toString, i.pos.line, i.msg)
    )
  }
}
