# Scala 3 `Fields` generation — design

Date: 2026-07-20 · Branch: `feature/scala3` · Status: approved

## Problem

The Scala 3 port of `@moka` uses the experimental `MacroAnnotation`, which
expands after typer/pickling. Members it adds are invisible to other
compilation units, so `ManyFields.Fields.a` can never typecheck from the test
project ("value Fields is not a member"). Additionally, sbt fails with a
"Double definition: object Fields" error (module trees built by hand instead
of `ClassDef.module`). The annotation approach is a dead end for the visible
`X.Fields.a` API.

## Decision (approach B)

Keep `@moka` for Scala 2. On Scala 3, generate `Fields` with a **typer-time
transparent inline macro** returning a `Selectable` instance with a refined
structural type — one `val <field>: String` refinement per case field:

```scala
// macros, scala-3
class FieldNames(values: Map[String, String]) extends Selectable:
  def selectDynamic(name: String): String = values(name)

transparent inline def generateFields[T]: FieldNames = ${ generateFieldsImpl[T] }
```

```scala
// examples, scala-3 Definitions
final case class ManyFields(a: Int, b: Int)
object ManyFields:
  val Fields = Moka.generateFields[ManyFields]
```

Interface compatibility is at the **call site**: `X.Fields.a` is identical
source on Scala 2 and 3 (definition files are already version-specific).
No `-experimental` needed for this path.

## Scope — incremental

1. **First milestone (this session):** plain case-field names only. Ignore
   `@BsonProperty`/`@bsonField` renames, custom object name (`@moka("Renamed")`),
   and existing-companion edge cases. Green = the two `ManyFields` assertions
   in the Scala 3 `MokaSpec` pass.
2. Then re-enable commented test cases one at a time and extend the macro
   (bson renames, custom name, richer companions).
3. Later: restore Scala 2 cross-build + shared spec.

## Cleanup

- Remove `-Xprint:postInlining` debug flag from build.sbt.
- Keep the (unused) `MacroAnnotation` code for reference; delete later.

## Verification

`bloop test examples` for the inner loop; final check with
`sbt "examples/clean; examples/test"` (sbt is the source of truth — bloop and
sbt disagreed on the old annotation code).
