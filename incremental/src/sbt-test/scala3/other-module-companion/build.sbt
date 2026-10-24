ThisBuild / scalaVersion := "3.3.8"

lazy val core = project.settings(
  libraryDependencies ++= Seq(
    "io.github.vimalaguti" %% "moka"     % sys.props("moka.version"),
    "dev.zio"              %% "zio-json" % "1.0.0"
  )
)

lazy val consumer = project.dependsOn(core)
