package io.moka

import java.nio.file.Files

import dotty.tools.dotc.Driver
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.interfaces.Diagnostic as Level
import dotty.tools.dotc.reporting.{Diagnostic as Dia, Reporter}

/** Compiles sources with the Scala 3 compiler in-process and returns every
  * diagnostic. munit's `compileErrors` shows neither warnings nor more than the
  * first error.
  */
object CompileHarness:
  final case class Diagnostic(severity: String, line: Int, msg: String)

  private lazy val classpath: String =
    scala.io.Source.fromResource("moka-compile-classpath.txt").mkString

  private final class Collecting extends Reporter:
    val all = scala.collection.mutable.ListBuffer.empty[Dia]
    def doReport(d: Dia)(using Context): Unit = all += d

  def compile(sources: (String, String)*): List[Diagnostic] =
    compileWith()(sources*)

  def compileWith(
      options: String*
  )(sources: (String, String)*): List[Diagnostic] =
    val dir = Files.createTempDirectory("moka-harness")
    val files = sources.map { (name, code) =>
      Files.writeString(dir.resolve(name), code).toString
    }
    val reporter = Collecting()
    Driver().process(
      (Seq("-classpath", classpath, "-d", dir.toString)
        ++ options ++ files).toArray,
      reporter
    )
    reporter.all.toList.map { d =>
      val severity = d.level match
        case Level.ERROR   => "ERROR"
        case Level.WARNING => "WARNING"
        case _             => "INFO"
      Diagnostic(severity, d.pos.line + 1, d.message)
    }
