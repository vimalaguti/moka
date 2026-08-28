---
slug: /
sidebar_position: 1
---

# Moka Macro

`Moka` generates a `Fields` object that gives access to the names of a case
class' fields at compile time — so MongoDB filters, updates and projections
don't need hardcoded strings, and a misspelled field name is a compile error
instead of a silent production bug.

```scala mdoc
import io.moka.*

case class Apple(color: String)
object Apple {
  val Fields = generateFields[Apple]
}

Apple.Fields.color
```

```scala mdoc:fail
Apple.Fields.colour // typo -> compile error
```

Fields inside sub-documents carry their full dotted path, and MongoDB's array
operators are generated too:

```scala mdoc
case class Engine(power: Int)
case class Car(engine: Engine, wheels: List[Engine])
object Car {
  val Fields = generateFields[Car]
}

Car.Fields.engine.power
Car.Fields.wheels._matched.power
```

Head over to [Features](features.md) for what the generated `Fields` object
can do.

## Supported Scala versions

| Scala          | mechanism                                        |
| -------------- | ------------------------------------------------ |
| 3 (3.3 LTS+)   | `generateFields[T]` inline macro            |
| 2.13           | `@moka` macro annotation                         |
| cross-compiled | both combined — same sources build on 2.13 and 3 |

See the [Scala 3](scala3.md), [Scala 2](scala2.md) and
[cross-compilation](cross.md) pages for the version-specific usage.

## Installation

Add this line to `build.sbt`:

```scala
libraryDependencies += "io.moka" %% "moka" % "@VERSION@"
```

:::note
Not yet published to a public repository — build it locally with
`sbt +publishLocal` for now.
:::
