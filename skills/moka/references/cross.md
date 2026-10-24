# moka in sources built for both Scala 2.13 and 3

Put `@moka` on the class **and** a placeholder val in an explicit companion:

```scala
import io.moka._   // `_`, not `*`: the file must parse on 2.13

@moka                                  // used by 2.13, ignored by Scala 3
case class Fruit(color: String)
object Fruit {
  val Fields = generateFields[Fruit]   // expanded by Scala 3, replaced by @moka on 2.13
}
```

- Add `-Ymacro-annotations` on the 2.13 axis only:

  ```scala
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 13)) => Seq("-Ymacro-annotations")
    case _             => Nil
  })
  ```

- The val name is the object name on both versions. For JSON names add
  `val JsonFields = generateJsonFields[Fruit]` (and/or `generateBsonFields`)
  next to it.
- All the Scala 2.13 rules apply: every nested case class gets the same
  `@moka` + `val Fields = generateFields[...]` pair, declared outside the
  object/class that holds the outer type. See [scala-2.13.md](scala-2.13.md).
