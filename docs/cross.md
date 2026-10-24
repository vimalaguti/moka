---
sidebar_position: 5
---

# Cross-compilation

The same source file can compile on Scala 2.13 **and** Scala 3: combine the
`@moka` annotation with the placeholder val in an explicit companion object.

```scala mdoc
import io.moka._

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

## Build setup

Only the 2.13 axis needs a flag, and shared sources must use the `_` wildcard
in imports — `import io.moka.*` is Scala 3 syntax and does not parse on 2.13
unless the whole project opts into `-Xsource:3`.

```scala
scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
  case Some((2, 13)) => Seq("-Ymacro-annotations")
  case _             => Nil
})
```

To rename the generated object in cross-compiled sources, rename the val: its
name is the object's name on both versions (on Scala 2 an annotation argument
such as `@moka("Params")` is ignored once placeholders are present):

```scala mdoc
@moka
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

Cross-compiled sources must follow two Scala 2.13 rules that Scala 3 does not
have.

**Every nested case class needs its own moka object** (since 0.2.0). On 2.13
the outer object takes the nested type's names from that object rather than
from its annotations, because scalac 2.13 drops zio-bson's `@bsonField` and
zio-json's `@jsonField` from classfiles — reading them again from another
module, a jar or after an incremental build would silently lose the rename.
Give the nested type the same `@moka` + placeholder pair, of the outer's format
(`generateJsonFields` under a `@mokaJson` outer; a type with no renames at all
is valid for every format):

```scala
@moka
case class Engine(@bsonField("hp") power: Int)
object Engine {
  val Fields = generateFields[Engine]
}

@moka
case class Car(engine: Engine)
object Car {
  val Fields = generateFields[Car]
}

Car.Fields.engine.power == "engine.hp"
```

The object is found by its format, not its name, so any val name works.
Without one, 2.13 fails with `moka: nested type Engine has no BSON moka
object, ...`. Value classes and plain values such as `String` are
leaves and never need one. See [Scala 2](scala2.md#nested-fields) for generic
nested types and the opt-out.

**The nested type may not be declared beside the annotated class.** The `@moka`
annotation expands *before* the typer runs, so a type declared as a member of
the **same enclosing object or class** as the annotated case class is invisible
to the macro, which fails with an error naming it:

```scala
object Model {
  @moka case class Engine(power: Int)
  object Engine { val Fields = generateFields[Engine] }

  @moka case class Car(engine: Engine) // error on Scala 2: Engine is not visible
  object Car { val Fields = generateFields[Car] }
}
```

Declare the nested type at package level, or in another file:

```scala
@moka case class Engine(power: Int)
object Engine { val Fields = generateFields[Engine] }

object Model {
  @moka case class Car(engine: Engine) // fine on both
  object Car { val Fields = generateFields[Car] }
}
```

A directly self-referential field stops descending and stays a plain name. A
mutually recursive pair — `A` holding a `B` that refers back to `A` — cannot be
expanded on Scala 2 at all, because resolving `B` re-enters `A`'s own expansion.
