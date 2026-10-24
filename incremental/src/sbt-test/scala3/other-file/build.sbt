scalaVersion := "3.3.8"
// Tiny builds would otherwise cross zinc's "recompile everything" threshold
// (half the sources invalidated) and hide what incremental compilation does.
incOptions ~= (_.withRecompileAllFraction(1.0))
libraryDependencies ++= Seq(
  "io.github.vimalaguti" %% "moka"     % sys.props("moka.version"),
  "dev.zio"              %% "zio-json" % "1.0.0"
)
