---
sidebar_position: 2
---

# Scala 2

On Scala 2.13 the `@moka` macro annotation does everything by itself — it
generates the companion object (or extends an existing one) with the `Fields`
object. Requires the `-Ymacro-annotations` compiler flag.

```scala
import io.moka.Moka.moka

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

## Full code

Every supported case is covered by the test suite:

- [Scala2Definitions.scala](https://github.com/vimalaguti/moka/blob/master/examples/src/main/scala-2/io/moka/Scala2Definitions.scala) — the annotated case classes
- [Scala2MokaSpec.scala](https://github.com/vimalaguti/moka/blob/master/examples/src/test/scala-2/io/moka/Scala2MokaSpec.scala) — the assertions
