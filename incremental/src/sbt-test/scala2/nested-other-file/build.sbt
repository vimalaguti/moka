// Tiny builds would otherwise cross zinc's "recompile everything" threshold
// (half the sources invalidated) and hide what incremental compilation does.
incOptions ~= (_.withRecompileAllFraction(1.0))

scalaVersion := "2.13.18"
scalacOptions ++= Seq("-Ymacro-annotations", "-Xsource:3")
libraryDependencies ++= Seq(
  "io.github.vimalaguti" %% "moka"     % sys.props("moka.version"),
  "dev.zio"              %% "zio-json" % "1.0.0"
)
