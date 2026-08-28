// Brings sbt-pgp (publishSigned) and sbt-dynver (version from git tags), and
// wires publishTo for the Central Portal. sbt 1.13 supplies sonaUpload /
// sonaRelease / localStaging itself, so no sbt-sonatype is involved.
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.12.1")
// addSbtPlugin("org.typelevel" % "sbt-tpolecat" % "0.5.0")
addSbtPlugin("org.scalameta"      % "sbt-mdoc"     % "2.6.1")
addSbtPlugin("com.github.sbt"     % "sbt-ghpages"  % "0.8.0")
addSbtPlugin("org.scalameta"      % "sbt-scalafmt" % "2.5.2")
addSbtPlugin("pl.project13.scala" % "sbt-jmh"      % "0.4.8")
