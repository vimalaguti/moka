---
sidebar_position: 4
---

# Scala 2

On Scala 2.13 the `@moka` macro annotation does everything by itself — it
generates the companion object (or extends an existing one) with the `Fields`
object. Requires the `-Ymacro-annotations` compiler flag.

```scala
import io.moka._

@moka
case class Simple(color: String)

Simple.Fields.color == "color"
```

An existing companion object is preserved:

```scala
@moka
case class WithCompanion(a: Int)
object WithCompanion {
  val default: WithCompanion = WithCompanion(0)
}

WithCompanion.Fields.a == "a"
```

The generated object can be renamed through the annotation argument:

```scala
@moka("Params")
final case class Renamed(a: Int)

Renamed.Params.a == "a"
```

## Nested fields

The annotation descends into nested case classes exactly as the cross-compiled
style does, with or without a companion object of your own:

```scala
final case class Engine(power: Int)

object Model {
  @moka
  final case class Car(engine: Engine, wheels: List[Engine])
}

Car.Fields.engine.power          == "engine.power"
Car.Fields.wheels._matched.power == "wheels.$.power"
```

Note where `Engine` is declared. Scala 2 has one restriction Scala 3 does not:
the nested type may **not** be a member of the same object or class as the
annotated case class, because the annotation macro runs before the typer and
cannot see it. moka fails with an error naming the type. See
[cross-compilation](cross.md) for the details.

## Full code

Every supported case is covered by the test suite:

- [Scala2Definitions.scala](https://github.com/vimalaguti/moka/blob/master/examples/src/main/scala-2/io/moka/Scala2Definitions.scala) — the annotated case classes
- [Scala2MokaSpec.scala](https://github.com/vimalaguti/moka/blob/master/examples/src/test/scala-2/io/moka/Scala2MokaSpec.scala) — the assertions
