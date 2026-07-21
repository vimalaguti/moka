---
sidebar_position: 3
---

# Scala 3

On Scala 3 no annotation is needed: `generateFields[T]` is an inline
macro that expands at typechecking time. Declare a `val` in the companion
object:

```scala mdoc
import io.moka.*

case class Simple(color: String)
object Simple {
  val Fields = generateFields[Simple]
}

Simple.Fields.color
```

The name of the "object" is simply the name of the val:

```scala mdoc
case class Renamed(a: Int)
object Renamed {
  val Params = generateFields[Renamed]
}

Renamed.Params.a
```

Using it on something that is not a case class is a compile error:

```scala mdoc:fail
generateFields[String]
```

Works on Scala 3.3 LTS and later, with no experimental features.

## Full code

Every supported case is covered by the test suite:

- [Scala3Definitions.scala](https://github.com/vimalaguti/moka/blob/master/examples/src/main/scala-3/io/moka/Scala3Definitions.scala) — the definitions
- [Scala3MokaSpec.scala](https://github.com/vimalaguti/moka/blob/master/examples/src/test/scala-3/io/moka/Scala3MokaSpec.scala) — the assertions
