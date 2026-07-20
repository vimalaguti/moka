---
slug: /
---

# Moka Macro

`Moka` generates a `Fields` object which allows to access the name of the
case class parameters at compile time. Works on Scala 2.13 and Scala 3
(3.3 LTS), including cross-compiled codebases sharing the same sources.

## Installation
To install, add this line into `build.sbt`
```scala
libraryDependencies += "io.moka" %% "moka" % "@VERSION@"
```
