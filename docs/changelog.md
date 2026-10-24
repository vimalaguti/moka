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
  ignored.
- Scala 2.13: a type declared beside the annotated class, sharing its name with
  an outer type, silently produced the outer type's names; it is now an error.
- Scala 2.13: `@moka` on a generic case class gives a clear error.
- Scala 2.13: a nested node two levels below a type from another run became a
  plain string.
- Scala 2.13: a node's `toString` was an object reference; on both versions it
  is now its path, so `s"${node}.x"` works.

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

See also [known limitations](features.md#known-limitations).

## 0.1.0 (2026-08-28)

Initial release.
