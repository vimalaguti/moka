---
sidebar_position: 4
---

# Cross-compilation

The same source file can compile on Scala 2.13 **and** Scala 3: combine the
`@moka` annotation with the placeholder val in an explicit companion object.

```scala mdoc
import io.moka.Moka
import io.moka.Moka.moka

@moka
case class Fruit(name: String, weight: Double)
object Fruit {
  val Fields = Moka.generateFields[Fruit]
}

Fruit.Fields.name
```

How it works, per version:

- **Scala 2** — the `@moka` annotation rewrites the companion before
  typechecking, replacing the `val Fields = Moka.generateFields[Fruit]`
  statement with the generated `object Fields`.
- **Scala 3** — the annotation is a no-op; `generateFields` is an inline
  macro that expands into a structurally-typed value during typechecking.

Either way, every call site looks the same (`Fruit.Fields.name`) and has the
same static type, so shared code — including shared tests — works unchanged
on both versions.

To rename the generated object in cross-compiled sources, rename the val and
pass the same name to the annotation (used by Scala 2 only):

```scala mdoc
@moka("Params")
case class Renamed(a: Int)
object Renamed {
  val Params = Moka.generateFields[Renamed]
}

Renamed.Params.a
```

## Full code

The shared test suite runs identical sources under both versions:

- [Definitions.scala](https://github.com/vimalaguti/moka/blob/master/examples/src/main/scala/io/moka/Definitions.scala) — cross-compiled definitions
- [MokaSpec.scala](https://github.com/vimalaguti/moka/blob/master/examples/src/test/scala/io/moka/MokaSpec.scala) — shared assertions
