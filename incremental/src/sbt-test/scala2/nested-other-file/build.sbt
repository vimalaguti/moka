scalaVersion := "2.13.18"
scalacOptions ++= Seq("-Ymacro-annotations", "-Xsource:3")
libraryDependencies ++= Seq(
  "io.github.vimalaguti" %% "moka"     % sys.props("moka.version"),
  "dev.zio"              %% "zio-json" % "1.0.0"
)
