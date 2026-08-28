---
slug: /
sidebar_position: 1
---

# Moka Macro

`Moka` generates a `Fields` object that gives access to the names of a case
class' fields at compile time — so MongoDB filters, updates and projections
don't need hardcoded strings, and a misspelled field name is a compile error
instead of a silent production bug.

It is a compile-time tool with **no dependencies but Scala itself** — not on a
MongoDB driver, not on a bson library, not on anything. See
[Dependencies](#dependencies).

```scala mdoc
import io.moka._

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
libraryDependencies += "io.github.vimalaguti" %% "moka" % "@VERSION@"
```

On **Scala 3** that is the whole setup — no compiler flags, no plugins.

:::warning[Scala 2.13 also needs one compiler flag]
On 2.13 `@moka` is a *macro annotation*, and scalac expands those only when
told to. Add `-Ymacro-annotations`:

```scala
// a 2.13-only project
scalacOptions += "-Ymacro-annotations"

// a cross-built project — the flag exists on the 2.13 axis only
scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
  case Some((2, 13)) => Seq("-Ymacro-annotations")
  case _             => Nil
})
```

Without it the annotation is silently left in place, no `Fields` object is
generated, and every selection fails with `value <field> is not a member of
io.moka.FieldsNotGenerated_AddYmacroAnnotations`. That error name is the
reminder: the flag is missing.
:::

Published to Maven Central, so no extra resolver is needed.

## Dependencies

**None, beyond the Scala standard library you already have.** moka's whole job
happens in the compiler; what it leaves behind at runtime is string constants
and one small trait of its own.

| Your Scala   | what moka adds to your classpath                |
| ------------ | ----------------------------------------------- |
| 3 (3.3 LTS+) | nothing but `moka_3` itself                     |
| 2.13         | nothing but `moka_2.13` itself                  |

Two consequences worth spelling out:

- **No bson or driver dependency.** moka understands `@BsonProperty`
  (mongo-scala-bson) and `@bsonField` (zio-bson), but it matches those
  annotations *by simple name* and never links against either library. Use one,
  use both, use neither — moka pulls in nothing either way, and adding moka
  cannot drag a driver version into your build.
- **`scala-reflect` does not reach you.** It appears in moka's 2.13 POM at
  `provided` scope because it is needed to *expand* the annotation macro inside
  the compiler, never at runtime. A downstream 2.13 project compiles and runs
  with only `moka_2.13.jar` and `scala-library` present.
