// sbt 2 builds of every plugin exist under the `_sbt2_3` artifact suffix, at the
// same version numbers — except these three, whose sbt-2 builds start later than
// the versions sbt 1 was pinned to.
//
// Brings sbt-pgp (publishSigned) and sbt-dynver (version from git tags), and
// wires publishTo for the Central Portal. sbt supplies sonaUpload /
// sonaRelease / localStaging itself, so no sbt-sonatype is involved.
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.12.1")
// addSbtPlugin("org.typelevel" % "sbt-tpolecat" % "0.5.0")
addSbtPlugin("org.scalameta"      % "sbt-mdoc"     % "2.9.1")
addSbtPlugin("com.github.sbt"     % "sbt-ghpages"  % "0.10.0")
addSbtPlugin("org.scalameta"      % "sbt-scalafmt" % "2.6.2")
addSbtPlugin("pl.project13.scala" % "sbt-jmh"      % "0.4.8")
// sbt 2 has no global plugins directory, so bloop is declared per project.
addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "2.1.2")
