lazy val scala213Version        = "2.13.16"
lazy val scala3LtsVersion          = "3.3.5"
lazy val scala3LastVersion          = "3.6.4"
lazy val supportedScalaVersions = List(scala213Version, scala3LastVersion)
lazy val updatedScalaVersions = List(scala213Version, scala3LastVersion)

ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "io.moka"
ThisBuild / organizationName := "moka"
ThisBuild / scalaVersion     := scala3LastVersion

lazy val scalacOptionsCommon = Seq(
  Compile / scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 13)) => List("-Ymacro-annotations", "Xsource:3")
      case Some((3, _))  => List("-experimental", "-Xprint:postInlining")
      case _             => Nil
    }
  }
)

lazy val root = project
  .settings(
    crossScalaVersions := Nil,
    publish / skip     := true,
    addCommandAlias("run", "examples/run")
  )
  .aggregate(examples, macros)

lazy val examples = project
  .settings(scalacOptionsCommon)
  .settings(
    crossScalaVersions := supportedScalaVersions,
    //   scalacOptions += "-Ymacro-debug-lite",
    libraryDependencies += ("org.mongodb.scala" %% "mongo-scala-bson" % "5.2.0")
      .cross(CrossVersion.for3Use2_13),
    libraryDependencies += "dev.zio"       %% "zio-bson" % "1.0.7",
    libraryDependencies += "org.scalameta" %% "munit"    % "1.0.2" % Test
  )
  .dependsOn(macros)

lazy val docs = project
  .in(file("moka-docs"))
  .settings(
    crossScalaVersions := supportedScalaVersions,
    moduleName     := "moka-docs",
    git.remoteRepo := "git@github.com:vimalaguti/moka.git"
  )
  .dependsOn(examples)
  .enablePlugins(MdocPlugin, DocusaurusPlugin, GhpagesPlugin)

lazy val macros = project
  .settings(scalacOptionsCommon)
  .settings(
    crossScalaVersions := supportedScalaVersions,
    name := "moka",
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 13)) =>
          Seq(
            "org.scala-lang" % "scala-reflect" % scalaVersion.value
          )
        case _ => Seq.empty
      }
    }
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
