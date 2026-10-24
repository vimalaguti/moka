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

Nested types need no moka object of their own — Scala 3 reads their annotations
from TASTy, which keeps them all — and may be declared anywhere, including beside
the class that uses them. Neither Scala 2 rule on the
[cross-compilation](cross.md#nested-types-on-scala-2) page applies here, unless
the same sources also build on 2.13:

```scala mdoc
object Bicycle {
  case class Cog(teeth: Int)
  case class Chainring(cogs: List[Cog])
  object Chainring {
    val Fields = generateFields[Chainring]
  }
}

Bicycle.Chainring.Fields.cogs._all.teeth
```

Works on Scala 3.3 LTS and later, with no experimental features.

Unlike Scala 2, where every selection compiles to a constant, a selection here
is a map lookup per level at runtime: negligible next to a database call, but
worth hoisting out of a hot loop. A typo's error prints the whole generated type,
which is long for a large model.

## Full code

Every supported case is covered by the test suite:

- [Scala3Definitions.scala](https://github.com/vimalaguti/moka/blob/master/examples/src/main/scala-3/io/moka/Scala3Definitions.scala) — the definitions
- [Scala3MokaSpec.scala](https://github.com/vimalaguti/moka/blob/master/examples/src/test/scala-3/io/moka/Scala3MokaSpec.scala) — the assertions
