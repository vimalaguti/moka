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

### Fixed

- Scala 2.13: `@bsonField` on a nested type compiled in another module, jar or
  incremental build was silently dropped, giving a wrong path.
- Java `@BsonProperty(value = "x")` with named arguments.
- `@BsonProperty("")` keeps the field name instead of producing `""`.

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

Otherwise compilation fails with `moka: nested type Engine has no BSON moka
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
