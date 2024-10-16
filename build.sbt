import Dependencies._

ThisBuild / scalaVersion     := "2.13.15"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "io.moka"
ThisBuild / organizationName := "moka"

lazy val root = project
  .settings(
    publishArtifact := false,
    addCommandAlias("run", "core/run")
  )
  .aggregate(core, macros)

lazy val core = project
  .settings(
  //   scalacOptions += "-Ymacro-debug-lite",
    scalacOptions += "-Ymacro-annotations",
    libraryDependencies += "org.mongodb.scala" %% "mongo-scala-bson" % "5.2.0",
    libraryDependencies += "dev.zio"           %% "zio-bson"         % "1.0.7",
    libraryDependencies += munit                % Test
  )
  .dependsOn(macros)

lazy val docs = project
  .in(file("moka-docs"))
  .settings(
    scalacOptions += "-Ymacro-annotations",
    mdocVariables := Map(
      "VERSION" -> version.value
    ),
    moduleName := "moka-docs",
    git.remoteRepo := "git@github.com:vimalaguti/moka.git"
  )
  .dependsOn(core)
  .enablePlugins(MdocPlugin, DocusaurusPlugin, GhpagesPlugin)

lazy val macros = project
  .settings(
    name := "Moka",
    scalacOptions += "-Ymacro-annotations",
    libraryDependencies += scalaMacros
    // libraryDependencies += munit % Test
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
