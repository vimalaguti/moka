---
sidebar_position: 5
---

# Cross-compilation

The same source file can compile on Scala 2.13 **and** Scala 3: combine the
`@moka` annotation with the placeholder val in an explicit companion object.

```scala mdoc
import io.moka.*

@moka
case class Fruit(name: String, weight: Double)
object Fruit {
  val Fields = generateFields[Fruit]
}

Fruit.Fields.name
```

How it works, per version:

- **Scala 2** — the `@moka` annotation rewrites the companion before
  typechecking, replacing the `val Fields = generateFields[Fruit]`
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
  val Params = generateFields[Renamed]
}

Renamed.Params.a
```

## Full code

The shared test suite runs identical sources under both versions:

- [Definitions.scala](https://github.com/vimalaguti/moka/blob/master/examples/src/main/scala/io/moka/Definitions.scala) — cross-compiled definitions
- [MokaSpec.scala](https://github.com/vimalaguti/moka/blob/master/examples/src/test/scala/io/moka/MokaSpec.scala) — shared assertions

## Nested types on Scala 2

Descending into a nested case class needs that type's field list, and on
Scala 2 the `@moka` annotation expands *before* the typer runs. A type declared
as a member of the **same enclosing object or class** as the annotated case
class is invisible to the macro, which fails with an error naming it:

```scala
object Model {
  case class Engine(power: Int)
  @moka case class Car(engine: Engine) // error on Scala 2: Engine is not visible
  object Car { val Fields = generateFields[Car] }
}
```

Declaring the nested type at package level, or in another file, is all that is
needed. Scala 3 has no such restriction, so this is the shape to use in
cross-compiled sources:

```scala
case class Engine(power: Int)

object Model {
  @moka case class Car(engine: Engine) // fine on both
  object Car { val Fields = generateFields[Car] }
}
```

A directly self-referential field stops descending and stays a plain name. A
mutually recursive pair — `A` holding a `B` that refers back to `A` — cannot be
expanded on Scala 2 at all, because resolving `B` re-enters `A`'s own expansion.
