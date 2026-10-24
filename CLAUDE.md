# CLAUDE.md

## What moka is

A macro library that generates a `Fields` object holding the (possibly BSON-renamed) field
names of a case class, so MongoDB filters/projections never hardcode strings and typos fail at
compile time. Artifact `moka`, package `io.moka`, groupId `io.github.vimalaguti`.

It supports **Scala 2.13 and Scala 3 from the same sources**. That constraint drives almost
every design decision here.

## Commands

```bash
sbt +testFull                                   # 2.13.18, 3.3.8, 3.9.0 — what CI runs
sbt testFull                                    # current version only (3.3.8 LTS)
sbt "++2.13.18; examples/testOnly io.moka.Scala2MokaSpec"  # 2.13-only suite
sbt "+scalafmtCheckAll; scalafmtSbtCheck"       # what CI checks
sbt "+scalafmtAll; scalafmtSbt"                 # fix formatting — `+` for the same reason
sbt docs/mdoc                                   # docs/*.md -> moka-docs/target/mdoc
sbt run                                         # demo; aliased to examples/run
sbt bloopInstall                                # .bloop/ is gitignored; regenerate before use
MOKA_DEBUG=1 sbt compile                        # Scala 3: print post-inlining trees
```

Modules: `root` (aggregates `macros`, `examples`, `examplesOptOut`), `macros` (the library,
`name := "moka"`), `examples` (demo + most tests), `examplesOptOut` (`examples-opt-out/`,
2.13-only sources compiled with the opt-out, which is global to a compilation — hence its own
project), `docs` (rooted at `moka-docs/`, mdoc + Docusaurus + ghpages). `bench` and `benchJmh`
are not aggregated, so CI never builds them. `incremental` (also not aggregated) holds sbt
`scripted` tests of incremental compilation, which munit cannot express; CI runs them as
`sbt "+macros/publishLocal; incremental/scripted"`. A `pending` script pins a known limitation. `root` must stay `.in(file("."))`: without it sbt
synthesizes a root that aggregates *every* project, which is how CI once compiled the benches.

**Three sbt 2 shapes that silently do the wrong thing:**

- **`test` is incremental** — it is sbt 1's `testQuick` and reports `Passed: Total 0` /
  `No tests to run` when nothing changed. `testFull` is sbt 1's `test`. A green `sbt test`
  proves nothing.
- **One command per argument.** `sbt a b` parses as the single command `a b` and fails with
  `Not a valid key`. Use `sbt "a; b"` — which works on sbt 1 too.
- **`+scalafmtCheckAll`, not `scalafmtCheckAll`.** A `scala-2` source directory is in the
  source set *only* while the current scalaVersion is 2.13, so a single-version check silently
  skips `Moka.scala`, `Scala2Definitions.scala` and `Scala2MokaSpec.scala` — and `scalafmtAll`
  will not reformat them either, which is how they once drifted unnoticed. `scalafmtSbtCheck`
  needs no `+`.

Website (Docusaurus 3, in `website/`): `npm install && npm start`, reading
`../moka-docs/target/mdoc` — run `sbt docs/mdoc` first or the site is empty. That path only
stays stable because `docs / mdocOut` is pinned in `build.sbt`; sbt 2's default would be
`target/out/jvm/<scalaVersion>/moka-docs/mdoc`.

## Architecture

### One API, two unrelated implementations

`macros/src/main/scala-2/io/moka/Moka.scala` and `macros/src/main/scala-3/io/moka/Moka.scala`
share only their public surface. Internally they have nothing in common:

- **Scala 2** — `@moka` is a whitebox macro annotation (`-Ymacro-annotations`) working on
  *untyped* parse trees via quasiquotes, rewriting the annottee into class + companion with
  `object Fields { val a: "a" = "a"; ... }`.
- **Scala 3** — `@moka` is an inert `StaticAnnotation`. The work is done by
  `transparent inline def generateFields[T]`, a typer-time macro returning a `FieldNames`
  (a `Selectable` over a `Map[String, Any]`) whose type is a `Refinement` chain adding one
  `ConstantType` member per case field.

So **every behaviour change must be made twice**, and the shared suite
`examples/src/test/scala/io/moka/MokaSpec.scala` is what keeps the two honest.

### Why Scala 3 is not an annotation macro

Recorded in `specs/specs/2026-07-20-scala3-fields-macro-design.md`. Scala 3 `MacroAnnotation`
expands *after* typer/pickling, so members it adds are invisible to other compilation units —
`Foo.Fields.a` could never typecheck from the test project. Do not "restore" the annotation
approach on Scala 3.

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
from the annotation argument. Breaking that match silently breaks all cross-compiled code — on
Scala 2 the placeholder only errors at refchecks, which munit's `compileErrors` never reaches
(see the note at the bottom of `Scala2MokaSpec`).

| Style | Sources | Tests |
|---|---|---|
| Cross-compiled (`@moka` + placeholder val) | `examples/src/main/scala/` | `MokaSpec` |
| Scala 2 only (`@moka` alone, companion generated) | `.../scala-2/` | `Scala2MokaSpec` |
| Scala 3 only (`generateFields`, no annotation) | `.../scala-3/` | `Scala3MokaSpec` |

Add a case to the matching `*Definitions.scala` and assert it in the matching suite — the
definitions files *are* the compile-time half of the test. munit's `compileErrors` returns
only the first error and no warnings (a macro abort ends it); for warnings, or errors after
an abort, use `CompileHarness` (`examples/src/test/scala-{2,3}`), which runs the compiler
in-process. Known limitations and their (ignored) tests: `KNOWN_LIMITATIONS.md`.

### Contract details that are load-bearing (tests assert them)

- **Literal types.** `Fields.a` has type `"a"`, not `String`, on **both** versions (`ValDef`
  with `tq"$name"` on 2; `ConstantType` refinement on 3).
- **Field renaming.** `@BsonProperty("x")` / `@bsonField("x")` / `@jsonField("x")`
  keep the Scala member name but change its *value and literal type*.
  Both macros match by **simple name only** — Scala 2 has no symbols at that phase and Scala 3 deliberately
  mirrors it, which is also why moka depends on neither bson nor json libraries.
  Scala 3 must read the primary-constructor param's annotations, not just the field's.
  `generateFields`/`@moka` and `generateBsonFields`/`@mokaBson` read only BSON annotations;
  `generateJsonFields`/`@mokaJson` only `@jsonField`. A `""` rename is absent (no rename, no
  conflict). zio-bson's `@bsonField` and zio-json's `@jsonField` extend plain `Annotation`, so
  **scalac 2.13 does not write them into classfiles** (the Java and mongo-scala `@BsonProperty` survive: Java / `StaticAnnotation`) —
  the reason for nested composition below.
- Existing companion members survive; one named like a generated object is an error. A custom
  object name comes from `@moka("Name")` on Scala 2, from the val name on Scala 3.
- The root `Fields` deliberately has **no** `_path` member (it addresses no sub-document), so
  a top-level field named `_path` cannot collide with an accessor.

**Nested descent.** A field whose type is a case class becomes a *node*, not a leaf `String`:
a real nested object extending `FieldPath` on Scala 2, a recursive `Refinement` over
`FieldPath` on Scala 3. `Fields.a.b` is the literal `"a.b"`; the node's own path is
`Fields.a._path`.

- **Every generated member is underscore-prefixed** (`_path`, `_matched`, `_all`); anything
  without one is the user's own field.
- `FieldPath` is a trait (`def _path: P`) in shared `macros/src/main/scala/`, with `syntax`.
  Only the two `Moka.scala` files and Scala 3's `FieldNames`/`PathNode` are version-specific.
- Scala 2 objects extend the trait directly and every selection folds to a constant (`ldc`).
  Scala 3 reaches `PathNode`s through `selectDynamic`, because an expression macro cannot
  introduce definitions — an object returned from an expression widens to `Object` and loses
  its members, so a structural refinement is the only way to expose names.
- `._path` is the default API; `import io.moka.syntax._` opts into an implicit
  `FieldPath[P] => P`. It is an `implicit def`, **not** a `given Conversion`: a wildcard import
  does not pick up `given`s on Scala 3 and Scala 2 cannot parse `import ...given`, so only
  `implicit def` gives cross-compiled sources one working import line. *Applying* the
  conversion breaks SemanticDB extraction for the whole file on Scala 3 (a warning, not an
  error; reproduces on 3.3/3.7/3.8) — importing without applying is free.
- `Option[X]` and single-element `Iterable`s are transparent (same dotted path). A *collection*
  of a descendable type also gets `_matched` (`$`) and `_all` (`$[]`) as descendable nodes —
  `Option` does not, and the operators do not nest.
- Descent stops at value classes, at `Map`, and at any type already on the path. Removing that
  cycle guard gives a compiler `StackOverflowError` on both versions.

**Nested composition** (ADR `specs/specs/2026-10-16-nested-composition-design.md`, ships as
0.2.0, breaking on 2.13). Every generated root carries one marker (`MokaBsonNames`,
`MokaJsonNames`, or neutral `MokaNames` = nothing renamed anywhere). On **2.13**, descending into a
non-generic case class `N` does not re-read `N`'s annotations (lost from classfiles): it looks up
`N`'s companion object with the matching or neutral marker and copies its literals, re-prefixed.
Shape (where descent stops) still comes from the outer. No such object → `moka:` error;
`-Xmacro-settings:moka.descendUnannotated` reads annotations anyway with a warning (the only way
for generic `N`). Composed initializers are spelled `N.Obj.member`, meant to let zinc record the dependency, but
a rename still does not recompile the outer file (`incremental` scripted test
`scala2/nested-other-file`, pending; see `KNOWN_LIMITATIONS.md`, L-stale). **Scala 3 never composes**: TASTy keeps
all annotations; it only emits the markers. The 2.13 code lives in `MokaExpander.scala`;
`Moka.scala` is the public surface.

### Docs pipeline

`docs/*.md` → mdoc (compiled against `examples`, so snippets are type-checked) →
`moka-docs/target/mdoc` → Docusaurus in `website/` → gh-pages branch. CI does **not** run
mdoc, so doc snippets can rot silently.

`docs/scala2.md` must use plain ` ```scala ` fences, never ` ```scala mdoc ` — its snippets
cannot compile on Scala 3. Other pages use `mdoc`, and `mdoc:fail` for the
typo-doesn't-compile demo.

## Repo-specific gotchas

- **`import io.moka.*` is Scala-3-only, and the test suite cannot catch it.** Every fixture
  lives in `package io.moka`, so no test writes the import at all, and `examples` sets
  `-Xsource:3` on 2.13, which would mask it anyway. Docs for 2.13 or cross-compiled users must
  say `import io.moka._`; only `docs/scala3.md` keeps `*`. The one real guard would be an
  external-consumer compile against `+publishLocal`, which CI does not do.
- **Forgetting `-Ymacro-annotations` used to produce `not a member of Unit`.** The
  `@compileTimeOnly` messages fire at refchecks, so any typer error preempts them. The 2.13
  placeholder therefore returns `io.moka.FieldsNotGenerated_AddYmacroAnnotations`, whose *name*
  is the diagnostic. `README.md` and `docs/intro.md` quote that string verbatim — keep them in
  sync if it is renamed.
- **The Scala 2 macro cannot see sibling types.** `c.typecheck` inside the pre-typer annotation
  macro fails for a type declared as a member of the *same enclosing object or class* as the
  annotated case class — the boundary is the enclosing owner, not the file, and declaration
  order is irrelevant. A sibling that shadows an outer type of the same simple name would make
  `c.typecheck` resolve to the *outer* type, so sibling names are checked first (`siblingNames`,
  via the deprecated `c.enclosingClass`). Top-level types in the same file, and types in other files, resolve
  fine. moka aborts with a diagnostic rather than degrading to a leaf. Mutually recursive
  models cannot be expanded at all on Scala 2: resolving one re-enters the other's expansion.
  `expand` keeps a per-thread stack of expansions in progress to catch that and report the
  cycle (`A -> B -> A`); without it the compiler overflows its stack.
- **sbt 2 restores stale compile outputs from its cache, even after `clean`.** Outputs live in
  the content-addressed `~/.cache/sbt/v2/cas` (`target/out/**` are symlinks into it), and the
  cache key does not notice that an in-build macro changed. After a macro edit, tests can run
  against `examples` classes expanded by the *old* macro — `macros/clean; examples/clean` is not
  enough. Force a fresh cache:
  `sbt "set Global / localCacheDirectory := file(\"/tmp/sbtcache-$(date +%s)\"); +clean; +testFull"`.
- **sbt-git's JGit cannot read a linked git worktree**: sbt fails to load in one unless a local,
  untracked `.sbt` file sets
  `ThisBuild / com.github.sbt.git.SbtGit.GitKeys.useConsoleForROGit := true`.
- `.scalafmt.conf` runs the `scala3` dialect globally with a `fileOverride` switching
  `src/{main,test}/scala-2/**` to `scala213source3`. New Scala-2-only sources must live under a
  `scala-2` directory or scalafmt mis-parses them.
- **sbt 2 has no global plugins directory.** Neither `~/.config/sbt/2/plugins/build.sbt` nor
  `~/.config/sbt/2/global.sbt` is read, and the sbt-1 plugins in `~/.sbt/1.0/plugins/` are
  invisible — so `sbt-bloop` is declared in this repo's `project/plugins.sbt`.
- **A killed sbt client leaves a server that hangs the next one.** sbt 2 runs a detached
  server per project (`sbt-launch.jar --detach-stdio`) and records it in
  `project/target/active.json`. Interrupt or time out a client and that server survives; the
  next invocation connects to it and waits forever with no output — it looks like sbt is
  compiling something enormous. Three of them once accumulated, holding 6.5 GB. Diagnose with
  `ps -eo pid,etimes,rss,args | grep '[s]bt-launch'` (mind that a `pkill -f sbt-launch` pattern
  matches its own shell and kills the script). Recover by killing the JVMs and removing
  `project/target/active.json` plus `~/.config/sbt/2/server/*`. End long sessions with
  `sbt shutdown`.
- `IMPROVEMENTS.md` is a local scratch list, hidden via `.git/info/exclude` rather than
  `.gitignore`. Never commit it; `git add -A` would.

## Publishing

Procedure, secrets and the rules that bite live in **`RELEASING.md`**. What matters when
changing code:

- **`version` is derived, not declared.** sbt-dynver reads it from git: a **`v`-prefixed** tag
  (`v0.1.0` → `0.1.0`; a bare `0.1.0` tag is ignored) on a **clean tracked tree**, otherwise
  `<tag>+<n>-<sha>-SNAPSHOT`. `publishTo` follows — `localStaging` for a release,
  `central-snapshots` otherwise — so a dirty tree cannot cut a release by accident. It also
  means `mdocVariables` renders `@VERSION@` as a dev version unless the site is built from a
  tagged checkout.
- **Cross-built on three versions, published for two.** `publishedScalaVersions` drops 3.9.0;
  `macros` builds there only because `examples` depends on it, so it carries
  `publish / skip := !publishedScalaVersions.contains(scalaVersion.value)`, which
  `publishSigned` honours. Verify with `sbt "++3.9.0; show macros/publish/skip"` (expects
  `true`).
- **`scala-reflect` is `Provided` on 2.13, deliberately.** It expands the annotation macro and
  is never needed at runtime — the generated code is string constants plus `FieldPath`.
  Verified: a downstream 2.13 project compiles and runs the macro with only `moka_2.13.jar` and
  `scala-library` present, because scalac hands its own `scala-reflect` to the macro
  classloader. Do not "fix" this back to compile scope.
- **No `sbt-sonatype`.** sbt ships the Central Portal tasks itself (`localStaging`,
  `sonaUpload`, `sonaRelease`) and turns `SONATYPE_USERNAME`/`SONATYPE_PASSWORD` into a
  `central.sonatype.com` credential. `sbt-ci-release` adds only `publishSigned` (sbt-pgp),
  `publishTo` and dynver — and its `ci-release` calls **`sonaRelease`**, not the
  `sonatypeBundleRelease` its README still documents (verified in the 1.12.1 jar).
  `release.yml` currently overrides that with `CI_SONATYPE_RELEASE: sonaUpload`, so a tag
  stages a bundle for manual promotion on the Portal rather than publishing outright —
  Maven Central releases cannot be withdrawn. See `RELEASING.md`.
