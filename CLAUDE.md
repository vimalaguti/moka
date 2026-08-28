# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What moka is

A macro library that generates a `Fields` object holding the (possibly BSON-renamed) field
names of a case class, so MongoDB filters/projections never hardcode strings and typos fail
at compile time. Published artifact name: `moka`, package `io.moka`.

It supports **Scala 2.13 and Scala 3.3 LTS from the same sources**. That constraint drives
almost every design decision in the repo.

## Commands

```bash
sbt compile                                     # current scalaVersion only (3.3.8 LTS)
sbt test                                        # ditto
sbt +test                                       # 2.13.18, 3.3.8 and 3.9.0 — what CI runs
sbt "examples/testOnly io.moka.MokaSpec"        # single suite (shared tests)
sbt "++2.13.18" "examples/testOnly io.moka.Scala2MokaSpec"  # 2.13-only suite
sbt "examples/testOnly io.moka.Scala3MokaSpec"             # 3.x-only suite
sbt scalafmtAll                                 # format
sbt scalafmtCheckAll scalafmtSbtCheck           # what CI checks
sbt examples/run                                # small demo (aliased as `sbt run`)
sbt docs/mdoc                                   # compile docs/*.md -> moka-docs/target/mdoc
MOKA_DEBUG=1 sbt compile                        # Scala 3: print post-inlining trees
```

Website (Docusaurus 3, in `website/`): `npm install && npm start`. It reads its docs from
`../moka-docs/target/mdoc`, so **run `sbt docs/mdoc` first** or the site has no content.

sbt modules: `root` (aggregate), `macros` (the library, `name := "moka"`), `examples`
(demo + all tests), `docs` (rooted at `moka-docs/`, mdoc + Docusaurus + ghpages).

## Architecture

### One API, two unrelated implementations

`macros/src/main/scala-2/io/moka/Moka.scala` and `macros/src/main/scala-3/io/moka/Moka.scala`
share only their public surface (`import io.moka.*` gives you `@moka` and `generateFields`).
Internally they have nothing in common:

- **Scala 2** — `@moka` is a whitebox macro annotation (`-Ymacro-annotations`) working on
  *untyped* parse trees via quasiquotes. It rewrites the annottee into class + companion,
  synthesising `object Fields { val a: "a" = "a"; ... }`.
- **Scala 3** — `@moka` is an inert `StaticAnnotation`. The work is done by
  `transparent inline def generateFields[T]`, a typer-time macro returning a `FieldNames`
  (a `Selectable` over a `Map[String, String]`) whose type is a `Refinement` chain adding one
  `ConstantType` member per case field.

Consequence: any behaviour change must be made **twice**, and the shared suite
`examples/src/test/scala/io/moka/MokaSpec.scala` is what keeps the two honest.

### Why Scala 3 is not an annotation macro

Recorded in `specs/specs/2026-07-20-scala3-fields-macro-design.md`. Short version: Scala 3
`MacroAnnotation` expands *after* typer/pickling, so members it adds are invisible to other
compilation units — `Foo.Fields.a` could never typecheck from the test project. Do not
"restore" the annotation approach on Scala 3.

### The cross-compilation contract

```scala
@moka                                    // Scala 2 reads it; Scala 3 ignores it
case class Fruit(name: String)
object Fruit {
  val Fields = generateFields[Fruit]     // Scala 3 expands it; Scala 2 replaces it
}
```

The placeholder `val X = generateFields[T]` is the hinge. On Scala 2 it is a
`@compileTimeOnly` stub; the annotation macro pattern-matches that val (`isGenerateFieldsCall`)
and swaps it for the generated object, taking the object's name **from the val's name**, not
from the annotation argument. Breaking that matching silently breaks all cross-compiled code —
on Scala 2 the placeholder only errors at refchecks, which `compileErrors` in munit does not
reach (see the note at the bottom of `Scala2MokaSpec`).

Three supported usage styles, each with its own source directory *and* its own test suite:

| Style | Sources | Tests |
|---|---|---|
| Cross-compiled (`@moka` + placeholder val) | `examples/src/main/scala/` | `MokaSpec` |
| Scala 2 only (`@moka` alone, companion generated) | `.../scala-2/` | `Scala2MokaSpec` |
| Scala 3 only (`generateFields`, no annotation) | `.../scala-3/` | `Scala3MokaSpec` |

When adding a case, add it to the matching `*Definitions.scala` and assert it in the matching
suite — the definitions files *are* the compile-time part of the test.

### Contract details that are load-bearing (tests assert them)

- **Literal types.** `Fields.a` has type `"a"`, not `String`, on **both** versions
  (`ValDef` with `tq"$name"` on 2; `ConstantType` refinement on 3).
- **BSON renaming.** `@BsonProperty("x")` (mongo-scala-bson) and `@bsonField("x")` (zio-bson)
  keep the Scala member name but change its *value and literal type* to `"x"`. Both macros
  match these annotations by **simple name only** — Scala 2 has no symbols at that phase, and
  Scala 3 deliberately mirrors it. Scala 3 also has to look at the primary-constructor param's
  annotations, not just the field's.
- Existing companion members must survive; a custom object name comes from `@moka("Name")` on
  Scala 2 and from the val name on Scala 3.
- **Nested descent.** A field whose type is a case class generates a *node* rather than a leaf
  `String`: on Scala 2 a real nested object extending `FieldPath`, on Scala 3 a recursive
  `Refinement` chain over `FieldPath`. `Fields.a.b` is the literal `"a.b"`; the node's own path
  is `Fields.a._path`. **Every generated member is underscore-prefixed** (`_path`, `_matched`,
  `_all`); anything without one is the user's own field. `FieldPath` is a **trait**
  (`def _path: P`) living in shared
  `macros/src/main/scala/` along with `syntax`; only the two `Moka.scala` macros and Scala 3's
  `FieldNames`/`PathNode` are version-specific. On Scala 2 the generated objects extend the
  trait directly and every selection folds to a constant (`ldc`); on Scala 3 they are `PathNode`
  instances reached through `selectDynamic`, because an expression macro cannot introduce
  definitions — an object returned from an expression is widened to `Object` and loses its
  members, so a structural refinement is the only way to expose names. `._path` is the
  default API; `import io.moka.syntax._` opts into an implicit `FieldPath[P] => P`. Applying
  that conversion breaks SemanticDB extraction for the whole file on Scala 3 (a warning, not an
  error; reproduces on 3.3/3.7/3.8) — importing without applying is free. That
  conversion is an `implicit def`, **not** a `given Conversion` — a wildcard import does not pick
  up `given`s on Scala 3, and Scala 2 cannot parse `import ...given`, so only `implicit def` gives
  cross-compiled sources one working import line.
  `Option[X]` and single-element `Iterable`s are transparent (same dotted path), but a
  *collection* of a descendable type also gets `_matched` (`$`) and `_all` (`$[]`) as descendable
  nodes — `Option` does not, and the operators do not nest. Descent stops
  at value classes, at `Map`, and at any type already on the path (cycle guard — removing it
  gives a compiler `StackOverflowError`, on both versions).
- The root `Fields` deliberately has **no** `path` member (`FieldNames` on Scala 3, a plain
  object on Scala 2) so that a top-level field named `path` doesn't collide with the accessor.

### Docs pipeline

`docs/*.md` → mdoc (compiled against `examples`, so snippets are type-checked) →
`moka-docs/target/mdoc` → Docusaurus in `website/` → gh-pages branch.

mdoc only ever runs on the current `scalaVersion` (3.3.8), so **`docs/scala2.md` must use plain
` ```scala ` fences, never ` ```scala mdoc `** — its snippets cannot compile on Scala 3. The
other pages use `mdoc` (and `mdoc:fail` for the typo-doesn't-compile demo).

## Repo-specific gotchas

- `.scalafmt.conf` runs the `scala3` dialect globally with a `fileOverride` switching
  `src/{main,test}/scala-2/**` to `scala213source3`. New Scala-2-only sources must live under a
  `scala-2` directory or scalafmt will mis-parse them.
- **The Scala 2 macro cannot see sibling types.** `c.typecheck` inside the pre-typer annotation
  macro fails for a type declared as a member of the *same enclosing object or class* as the
  annotated case class — the boundary is the enclosing owner, not the file, and declaration
  order is irrelevant. Top-level types in the same file and types in other files resolve fine.
  moka aborts with a diagnostic rather than degrading to a leaf. Mutually recursive models
  cannot be expanded at all on Scala 2 (re-entrant annotation expansion → `StackOverflowError`).
- **zinc does not always invalidate `examples` when only a macro body changes.** If a macro edit
  seems to have no effect, `sbt "++<ver>" macros/clean examples/clean <task>` before concluding
  the logic is wrong.
- The checked-in `.bloop/` config is **stale** (exported for Scala 3.6.4; the build now targets
  3.3.8) and holds only one Scala version. Run `sbt bloopInstall` before using the bloop-build
  skill, and use plain sbt for anything cross-version (`+test`, `++2.13.18 ...`).
- `.github/copilot-instructions.md` is **out of date**: it refers to a `core` module (now
  `examples`) and `scala-2.13` source dirs (now `scala-2`), and predates the Scala 3 support.
  Prefer this file.
- `IMPROVEMENTS.md` is an untracked local scratch list of follow-ups; it is not part of the
  build and is marked "do not commit".
- **Cross-built on three Scala versions, published for two.** `supportedScalaVersions` is
  2.13.18 / 3.3.8 LTS / 3.9.0; `publishedScalaVersions` drops 3.9.0. `macros` has to *build* on
  3.9.0 only because `examples` depends on it, so it carries
  `publish / skip := !publishedScalaVersions.contains(scalaVersion.value)` — `+publish` cannot
  emit a 3.9.0 artifact. Verify with `sbt "++3.9.0" "show macros/publish/skip"` (expects `true`).
- Publishing is otherwise not set up yet (`version := 0.1.0-SNAPSHOT`, `publish / skip` on every
  module but `macros`); the intended groupId is `io.github.vimalaguti` with the package staying
  `io.moka`.
