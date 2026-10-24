# Nested names by composition — design

Date: 2026-10-16 · Status: accepted

> This document records a decision and the reasoning behind it. It is **not** a
> description of current behaviour — read the code for that.

## Part A — What changes

On Scala 2.13, when moka descends into a nested case class `N`, the names come
from the moka object `N`'s own companion declares — built where `N`'s
annotations were visible — not from re-reading `N`'s annotations. On Scala 2.13 that is the only way
names survive separate or incremental compilation: zio-bson `@bsonField` and
zio-json `@jsonField` extend plain `Annotation`, which scalac does not write into
classfiles.

```scala
// module A                                         // module B (later run)
@moka case class Inner(@bsonField("zr") a: Int)     @moka case class Outer(inner: Inner)
object Inner { val Fields = generateFields[Inner] }  object Outer { val Fields = generateFields[Outer] }

Outer.Fields.inner.a : "inner.zr"   // 2.13 before this change: "inner.a", silently wrong
```

Rules, on both versions unless stated:

1. **Markers.** A generated root object/value carries exactly one of three
   unrelated marker traits, implemented by its runtime class: `MokaBsonNames`
   (`generateFields`, `generateBsonFields`, `@moka`, `@mokaBson`),
   `MokaJsonNames` (`generateJsonFields`, `@mokaJson`), or `MokaNames` —
   *neutral*, valid for every format — when no field anywhere in the object is
   renamed by any format's annotation and every composed sub-object is neutral.
   *Renamed* means carrying a renaming annotation with a non-empty value, of any
   format, even one equal to the field name. An object built by reading a nested
   type's annotations under the 2.13 opt-out is never neutral. A placeholder's
   call decides the format, not the annotation.
2. **Lookup (2.13).** For a field whose element type (after `Option`/collection
   unwrapping) is a non-generic case class `N`, in format `F`, the candidates are
   `N`'s companion's own vals and objects (2.13: objects) carrying `F`'s marker
   or the neutral one. One → use it. Several with identical members → use any.
   Several that differ → error. Lookup happens only where no enclosing object
   has been composed: below a composed `N`, every name comes from `N`'s object.
3. **Shape from the outer, names from `N`.** Where nodes stop is decided exactly
   as direct descent decides it today (value classes, `Map`, the cycle guard on
   the outer's path); every literal — leaf, `_path`, `N`'s own `_matched`/`_all`
   — is `N`'s object's literal re-prefixed with the field's path. An outer
   collection field's `_matched`/`_all` compose `N` again under `p.$`/`p.$[]`.
   **Scala 3 never composes:** it reads `N`'s annotations (TASTy keeps them all)
   and produces the same names and shape; it still emits the markers.
4. **No matching object (2.13).** Compile error,
   whether `N` comes from source or a classfile, telling the user to add `@moka`
   and a placeholder of format `F` to `N`. Opt-out:
   `-Xmacro-settings:moka.descendUnannotated` reads annotations anyway and warns
   naming `N`. Leaves (value classes, opaque types, `String`…) never need an
   object.
5. **Empty name is absent.** A renaming annotation with value `""` leaves the
   field name unchanged and takes no part in conflict detection.
6. **Scala 2 companion rewriting.** `@moka(name = "X")` equals `@moka("X")`. The
   first moka annotation to expand handles the whole stack and removes the
   others: with placeholders, exactly those objects are generated (annotation
   names ignored); without, one object per stacked annotation. A real companion
   is always emitted. A hand-written object colliding with a generated name is a
   compile error.

Scala 3 is not made stricter. Ships as **0.2.0** (breaking on 2.13).

## Part B — Record

### Why composition (the Magnolia / zio-bson model)

zio-bson on Scala 2 reads annotations through `Symbol.annotations` like moka
and hits the same limit (`Pickler.scala:307` writes only static annotations).
It works for nested types only because each codec is derived once in the
nested type's companion, in the run that has its source, and outer derivations
reuse that compiled codec via implicit search. moka's equivalent of the
compiled codec is the generated object: its literal member types are pickled
even when the annotation is not.

Verified on 2.13.18 before acceptance: the annotation macro can typecheck a
nested type and read its companion's generated object with `ConstantType`
members, both for classfile types and for same-run types (on-demand expansion,
including types declared later in the same file). `sym.pos == NoPosition`
identifies classfile types. On 3.3.8 a companion val's inferred refined type,
marker included, is readable from another file or later in the same file.

### Decisions and rejected alternatives

- **Type class + implicit search.** Scala 3 rejects
  `implicit val Fields = generateFields[T]` ("type of implicit definition needs
  to be given explicitly"); an explicit type widens away the refinement;
  `transparent inline given` is not Scala 2 syntax. Companion-member lookup
  searches where implicit search would look first.
- **Lookup by fixed object name.** Breaks custom names silently.
- **2.13 error only for classfile types.** Rejected after review: zinc loads
  same-module types from classfiles on incremental builds, so the same code
  would compile clean and fail incrementally (Metals). The error is
  unconditional on 2.13; the cost is that "only the outermost class needs
  `@moka`" no longer holds there. Considered and rejected: gating the error on
  zio-bson/zio-json being on the classpath (narrower breakage, but a rule that
  depends on the classpath, and same-module annotation loss still possible).
- **Warning only.** A wrong path would still compile.
- **Opt-out as an annotation argument.** Would widen the cross-version
  annotation signature for a 2.13-only concern.
- **Composition decides shape too.** Rejected: `N`'s object stopped at its own
  cycles (path `{N}`), the outer stops at its (`{root, …, N}`), so composed
  shape would differ from direct descent and, on Scala 3, depend on typing
  order (a `CyclicReference` was reproduced). Splitting shape (outer) from
  names (`N`) makes "composition never changes what direct descent with full
  annotation visibility produces" true by construction.
- **Scala 3 composing too, with annotation reading as fallback.** Rejected
  after review: names and shape are identical either way, but two routes would
  make the ambiguity error (rule 2) fire on one route only, and forcing a
  companion val whose type reaches the current path transitively
  (`Outer(n: N)`, `N(m: M)`, `M(o: Outer)`) raises a `CyclicReference`. Reading
  annotations is always correct on Scala 3, so composition is a 2.13 mechanism;
  the observable contract is shared.
- **Duplicate objects of one format are an error.** Rejected: the stacked
  `Fields` + `BsonFields` pattern is supported; only differing ones are
  ambiguous.
- **Marker hierarchy** (`MokaBsonNames extends MokaNames`). Rejected: "neutral"
  must be distinguishable from "specific"; three unrelated traits keep the test
  a plain subtype check. Prefixed names avoid clashes under `import io.moka._`.
- **Stacked annotations each deciding for themselves.** 2.13 expands them
  left-to-right on the previous output, so the second cannot see the
  placeholders the first consumed; and an annotation that creates a companion
  which a later one rewrites leaves no companion classfiles
  (`ClassNotFoundException` at runtime, reproduced).
- **Limiting 2.13 to BSON `generateFields`.** Considered while the lost
  annotations looked unfixable; composition removes the reason.

### Invariants

- Generated code is still constant-folded; `N`'s object is referenced by type
  only.
- Every generated member is underscore-prefixed; markers add no members. A
  leading underscore is never interpreted (Mongo `_id` is a user field).
- All moka errors start with `moka:` and name the type, the format and the fix.

### Breaking changes (0.2.0)

- 2.13: nested descendable types need their own moka object (or the opt-out).
  Objects compiled by 0.1.x carry no marker, so their types count as having
  none; the error says to recompile them.
- 2.13: generic nested case classes (cannot carry `@moka` there) need the
  opt-out, which is global: it disables the protection for every type in the
  build, not just the generic one.
- `""` renames no longer yield an empty path; a hand-written colliding
  companion object is now an error.

### Residual risks and scope

- Recursive models across types remain unsupported on 2.13 (mutual expansion
  overflows the stack, as before).
- Generic `N` is never composed. Reading objects produced by Scala 3 from 2.13
  (`-Ytasty-reader`) is out of scope.
- **Stale names after incremental builds — accepted, documented on the
  website.** zinc does not treat a change to a renaming annotation as an API
  change, so users of `N` in other files keep the old literal until a clean
  build (verified on 3.3.8, within a module and from tests).
  *Update 2026-10-24:* the scripted tests in `incremental/` show this on 2.13
  too: the composed `N.Obj.member` reference does not make zinc recompile the
  outer file. See `KNOWN_LIMITATIONS.md`, L-stale. Fixing it on Scala
  3 would need a dependency on `N`'s companion, which composition there rules
  out (`CyclicReference`). Users are told to clean-build after changing a
  rename.
- Moka matches annotations by simple name, so it cannot treat the Scala and
  Java `BsonProperty` differently.
