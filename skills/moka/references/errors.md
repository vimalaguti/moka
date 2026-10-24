# moka compile errors

| Message (fragment) | Version | Fix |
|---|---|---|
| `value x is not a member of io.moka.FieldsNotGenerated_AddYmacroAnnotations` | 2.13 | Add `scalacOptions += "-Ymacro-annotations"`, and `@moka` on the case class. |
| `value Fields is not a member of object X` | 2.13 | Same: flag missing or `@moka` missing. |
| `@moka was not expanded` / `io.moka.generateFields is a placeholder that @moka rewrites` | 2.13 | Same. |
| `object * is not a member of package io.moka` | 2.13 | Use `import io.moka._`. |
| `value x is not a member of io.moka.FieldNames` | 3 | Remove the type ascription: `val Fields = generateFields[X]`. |
| `value colour is not a member of ...X.Fields...` | both | Typo or renamed field: use the Scala field name. |
| `Required: String` / `type mismatch; found: X.Fields.n.type required: String` | both | A nested node was passed as a field name: use `X.Fields.n._path`. |
| `moka: class X is not a case class.` / `generateFields[X] requires a case class` | 2.13 / 3 | moka works on case classes only. |
| `moka: X is generic: on Scala 2.13 generic case classes cannot carry moka annotations` | 2.13 | Remove `@moka`; outer types reach it via `-Xmacro-settings:moka.descendUnannotated`. |
| `moka: the companion of X already defines Fields, which moka generates.` | 2.13 | Rename your own `Fields` member, or name the generated object differently (`@moka("Paths")`). |
| `moka: conflicting BSON renaming annotations on field 'f': ...` (or `JSON`) | both | Two renames of one format disagree: keep one. |
| `moka: nested types without a BSON moka object` (lists `N (at path): add @moka`) | 2.13 | Add `@moka` to each listed `N` (cross-compiled: plus `val Fields = generateFields[N]` in its companion). Built with moka 0.1.x? Recompile it. |
| `moka: nested types without a JSON moka object` | 2.13 | Add `@mokaJson` to each (cross-compiled: `val JsonFields = generateJsonFields[N]`). |
| `N (at path): it is generic, so it cannot carry one` | 2.13 | Compile with `-Xmacro-settings:moka.descendUnannotated`. |
| `N (at path): it carries a moka annotation, but its own expansion failed` | 2.13 | Fix the error reported at `N` first. |
| `not found: value Fields` with `@moka` alone | 2.13 | `-Ymacro-annotations` is missing. |
| warning `moka: X's fields are renamed only by JSON annotations` | both | Use `generateJsonFields` if Mongo stores the JSON names; else silence (Scala 3: `@nowarn("msg=moka")`; 2.13: `-Wconf:msg=renamed only by:s`). |
| `moka: cannot resolve type 'N' of field 'f' while expanding @moka on X.` | 2.13 | `N` is declared inside the same object/class as `X` (or doesn't exist): move it to package level or another file. |
| `java.lang.StackOverflowError` while compiling | 2.13 | Two case classes refer to each other: break the cycle. |
| warning `moka: reading the annotations of nested type N directly` | 2.13 | Expected under the opt-out; give `N` its own `@moka` to keep zio-bson/zio-json renames. |
