---
sidebar_position: 6
---

# Changelog

## 0.2.0 (unreleased)

### Added

- **JSON names**: `generateJsonFields` / `@mokaJson` build a `JsonFields` object
  from zio-json's `@jsonField`. `generateFields` stays BSON-only.
- **Explicit BSON selector**: `generateBsonFields` / `@mokaBson` (`BsonFields`).
- **Format markers**: generated objects are typed `MokaBsonNames`,
  `MokaJsonNames` or `MokaNames` (nothing renamed).
  [Details](features.md#format-markers).
- Scala 2.13: `@moka(name = "X")`, and stacking `@moka @mokaBson @mokaJson`.
- A warning when a type is renamed only by the other format's annotations
  (e.g. `@jsonField` under `generateFields`).
- Scala 2.13: one error lists every nested type missing its moka object; a
  nested type whose own expansion failed is reported as such.

### Fixed

- Scala 2.13: `@bsonField` on a nested type compiled in another module, jar or
  incremental build was silently dropped, giving a wrong path.
- Java `@BsonProperty(value = "x")` with named arguments.
- `@BsonProperty("")` keeps the field name instead of producing `""`.
- Scala 2.13: meta-annotated renames such as `@(BsonProperty @field)("x")` were
  ignored, on the annotated class and, under the opt-out, on nested types.
- Scala 2.13: a type declared beside the annotated class, sharing its name with
  an outer type, silently produced the outer type's names; it is now an error.
- Scala 2.13: `@moka` on a generic case class gives a clear error.
- Scala 2.13: mutually recursive case classes give a clear error naming the
  cycle instead of crashing the compiler with `StackOverflowError`.
- Scala 3: `generateFields` on a type alias of a case class.
- `IntMap` and `LongMap` fields were descended into as collections; like `Map`,
  they are now leaves.
- A generic type nested in itself (`Box[Box[X]]`) stopped at the inner `Box`
  as if it were a cycle.
- Scala 2.13: a field typed as another package's class of the same name
  (`case class Event(prev: legacy.Event)`) was a leaf; it is now composed,
  as on Scala 3.
- Scala 2.13: a nested node two levels below a type from another run became a
  plain string.
- Scala 2.13: a node's `toString` was an object reference; on both versions it
  is now its path, so `s"${node}.x"` works.
- Scala 2.13: a field type that does not resolve (a typo, a missing import)
  reported the sibling-type limitation instead of the compiler's own error.
- Scala 2.13: an invalid regex in `@nowarn("msg=...")` crashed the macro.
- Scala 3: renaming conflicts and the other-format warning point at the field
  instead of the `generateFields` call. To silence that warning, put `@nowarn`
  on the case class or the field, as on Scala 2.13; on the
  `val Fields = generateFields[...]` placeholder it no longer applies.

### Breaking changes and migration

**Scala 2.13 only: every nested case class needs its own moka object**, in the
outer's format (one with no renames serves both formats):

```scala
@moka
final case class Engine(@bsonField("hp") power: Int)
object Engine {
  val Fields = generateFields[Engine]
}
```

Otherwise compilation fails with `moka: nested types without a BSON moka
object`. Rebuild models compiled with moka 0.1.x. For types you cannot annotate,
or generic ones, compile with `-Xmacro-settings:moka.descendUnannotated`
([opting out](scala2.md#opting-out)).

On both versions:

- Two renames of the same format that disagree on one field are a compile error
  (0.1.0 used the first). Keep one.
- Scala 2.13: a companion member named like a generated object (e.g. your own
  `object Fields`) is a compile error. Rename it.
- An `IntMap`/`LongMap` field is a leaf: `m.x`, `m._matched` and `m._all` no
  longer exist. Address its values as with any `Map`.
- `Box[Box[X]]` descends into the inner `Box`, so its field is a node: use
  `b._path` where the field itself was used as a string.
- Scala 2.13: a field typed as another package's same-named class is a node
  (or needs that class's moka object), not a leaf.

After changing a renaming annotation, **do a clean build**: code in other files
keeps the old name otherwise
([stale names](features.md#stale-names-after-changing-a-rename)).

See also [known limitations](features.md#known-limitations).

## 0.1.0 (2026-08-28)

Initial release.
