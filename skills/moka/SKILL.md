---
name: moka
description: Use the moka Scala macro library (io.moka, artifact "io.github.vimalaguti" %% "moka") to get compile-checked MongoDB/BSON and JSON field names from case classes. Use this skill whenever a Scala project adds moka to its build, writes `@moka`, `@mokaBson`, `@mokaJson`, `generateFields`, `generateBsonFields` or `generateJsonFields`, builds MongoDB filters or updates from case-class fields (`Fields.x`, `_path`, `_matched`, `_all`), or hits a compile error mentioning `moka:` or `FieldsNotGenerated_AddYmacroAnnotations` — even if the user only says "field names for my Mongo query" in a project that depends on moka.
---

# moka

moka generates a `Fields` object from a case class whose members are the
field names (BSON/JSON-renamed where annotated) as literal-typed strings, so a
misspelled field in a MongoDB query fails at compile time. Describes moka 0.2.0.

Pick the project's Scala version and read only that file:

- Scala 3 → [references/scala-3.md](references/scala-3.md)
- Scala 2.13 → [references/scala-2.13.md](references/scala-2.13.md)
- Same sources built for both (rare) → [references/cross.md](references/cross.md)

Compile error from moka → [references/errors.md](references/errors.md)
