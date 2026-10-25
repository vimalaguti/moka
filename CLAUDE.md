# CLAUDE.md

moka is a macro library generating a `Fields` object of (possibly renamed) field names for
MongoDB filters/projections. It supports **Scala 2.13 and Scala 3 from the same sources**, and
that constraint drives almost every design decision. User-facing behaviour is in `docs/`
(start with `features.md`); known limitations in `KNOWN_LIMITATIONS.md`; releases in
`RELEASING.md`.

## Commands

```bash
sbt +testFull                                   # 2.13.18, 3.3.8, 3.9.0 — what CI runs
sbt "++2.13.18; examples/testOnly io.moka.Scala2MokaSpec"
sbt "+scalafmtCheckAll; scalafmtSbtCheck"       # what CI checks (`+scalafmtAll; scalafmtSbt` fixes)
sbt "+macros/publishLocal; incremental/scripted" # incremental-compilation tests (CI runs them)
MOKA_DEBUG=1 sbt compile                        # Scala 3: print post-inlining trees
sbt bloopInstall                                # .bloop/ is gitignored; regenerate before use
```

**sbt 2 shapes that silently do the wrong thing:**

- **`test` is incremental** (sbt 1's `testQuick`): `Passed: Total 0` when nothing changed. A
  green `sbt test` proves nothing; use `testFull`.
- **One command per argument.** `sbt a b` is the single command `a b`. Use `sbt "a; b"`.
- **`+scalafmtCheckAll`, not `scalafmtCheckAll`.** `scala-2` source directories exist in the
  source set only while scalaVersion is 2.13, so a single-version check (or `scalafmtAll`)
  silently skips `Moka.scala`, `MokaExpander.scala` and the 2.13 specs.
- **Stale outputs survive `clean`.** Outputs live in the content-addressed
  `~/.cache/sbt/v2/cas`, whose key does not notice an in-build macro change, so tests can run
  against `examples` expanded by the *old* macro. After a macro edit:
  `sbt "set Global / localCacheDirectory := file(\"/tmp/sbtcache-$(date +%s)\"); +clean; +testFull"`.
- **A killed client leaves a server that hangs the next one.** sbt 2 runs a detached server
  (`sbt-launch.jar --detach-stdio`, recorded in `project/target/active.json`); the next client
  connects to it and waits forever with no output. Diagnose with
  `ps -eo pid,etimes,rss,args | grep '[s]bt-launch'` (a `pkill -f sbt-launch` pattern kills its
  own shell). Recover by killing the JVMs and removing `project/target/active.json` plus
  `~/.config/sbt/2/server/*`. End long sessions with `sbt shutdown`.
- **`++` sticks in that server.** After `sbt "++2.13.18; ..."`, the next plain
  `sbt testOnly ...` silently runs on 2.13 too. Start every single-version command with an
  explicit `++` (`++3.3.8` for the default).
- **No global plugins directory** (`~/.config/sbt/2/plugins`, `~/.sbt/1.0/plugins` are not
  read), hence `sbt-bloop` in `project/plugins.sbt`.
- **sbt-git's JGit cannot read a linked git worktree**: sbt fails to load in one unless an
  untracked `.sbt` file sets
  `ThisBuild / com.github.sbt.git.SbtGit.GitKeys.useConsoleForROGit := true`.

**Build layout traps.** `root` must stay `.in(file("."))`: otherwise sbt synthesizes a root
aggregating *every* project, which is how CI once compiled the unaggregated benches.
`examplesOptOut` is its own project because `-Xmacro-settings:moka.descendUnannotated` is
global to a compilation. `docs / mdocOut` is pinned in `build.sbt` so the website's
`../moka-docs/target/mdoc` stays stable (run `sbt docs/mdoc` before `npm start` in `website/`).

## Architecture

### Two unrelated implementations

`macros/src/main/scala-2/` and `macros/src/main/scala-3/` share only the public surface:

- **Scala 2**: `@moka` is a whitebox macro annotation working on *untyped* trees before the
  typer (`MokaExpander.scala`; `Moka.scala` is the public surface).
- **Scala 3**: `@moka` is inert; `transparent inline def generateFields[T]` returns a
  `FieldNames` (a `Selectable` over a `Map`) typed as a `Refinement` chain of `ConstantType`s.

So **every behaviour change must be made twice**; the shared suite
`examples/src/test/scala/io/moka/MokaSpec.scala` keeps them honest.

**Do not "restore" a `MacroAnnotation` on Scala 3** (`specs/specs/2026-07-20-scala3-fields-macro-design.md`):
it expands after typer/pickling, so its members are invisible to other compilation units.
Scala 3 nodes go through `selectDynamic` because an expression macro cannot introduce
definitions: a returned object widens to `Object`, so a structural refinement is the only way
to expose names.

### The cross-compilation hinge

```scala
@moka                                    // Scala 2 reads it; Scala 3 ignores it
case class Fruit(name: String)
object Fruit { val Fields = generateFields[Fruit] }  // Scala 3 expands; Scala 2 replaces
```

On Scala 2 the placeholder is a `@compileTimeOnly` stub that the annotation macro
pattern-matches and swaps for the generated object, **naming it after the val**. Breaking that
match silently breaks all cross-compiled code: the stub only errors at refchecks, which munit's
`compileErrors` never reaches. For the same reason the 2.13 stub returns
`io.moka.FieldsNotGenerated_AddYmacroAnnotations`, whose *name* is the diagnostic for a missing
`-Ymacro-annotations` (any typer error preempts refchecks); `README.md` and `docs/intro.md` quote
it verbatim.

**Tests.** Fixtures go in the `*Definitions.scala` matching the style (`examples/src/main/scala`
cross-compiled, `.../scala-2`, `.../scala-3`); the definitions files *are* the compile-time half
of the test. munit's `compileErrors` returns only the first error and no warnings; for
warnings, positions, or errors after an abort, use `CompileHarness`
(`examples/src/test/scala-{2,3}`), which runs the compiler in-process.

### Why things are the way they are

- **Renames match annotations by simple name only.** Scala 2 has no symbols at that phase, and
  Scala 3 mirrors it; that is why moka depends on no bson/json library. Scala 3 must read the
  primary-constructor param's annotations, not just the field's.
- **`syntax` is an `implicit def`, not a `given Conversion`**: a wildcard import does not pick
  up `given`s on Scala 3 and Scala 2 cannot parse `import ...given`.
- **Every generated member is underscore-prefixed** (`_path`, `_matched`, `_all`), so anything
  without one is a user field; the root has no `_path`, so a field named `_path` cannot collide.
- **The cycle guard is load-bearing**: removing it overflows the compiler stack on both versions.
- **Nested composition (2.13 only**, `specs/specs/2026-10-16-nested-composition-design.md`).
  scalac 2.13 does not write zio's `@bsonField`/`@jsonField` (plain `Annotation`) into
  classfiles. So descending into a nested case class reads no annotations: it copies the
  literals of that class's companion object carrying the matching or neutral marker
  (`MokaBsonNames`/`MokaJsonNames`/`MokaNames`). Scala 3 never composes (TASTy keeps
  annotations); it only emits the markers.
- **The Scala 2 macro cannot see sibling types.** `c.typecheck` in the pre-typer macro fails
  for a type declared in the same enclosing object or class as the annottee (the boundary is
  the owner, not the file). A sibling shadowing an outer same-named type would resolve to the
  *outer* one, so sibling names are checked first (`siblingNames`). Mutually recursive models
  re-enter each other's expansion; a per-thread expansion stack reports the cycle instead of
  overflowing.
- **`scala-reflect` is `Provided` on 2.13, deliberately**: scalac hands the macro its own, and
  generated code needs none at runtime. Do not "fix" it to compile scope.

## Gotchas

- **`import io.moka.*` is Scala-3-only, and no test can catch it** (fixtures live in
  `package io.moka`, and `examples` sets `-Xsource:3`). Docs for 2.13 or cross-compiled users
  must say `import io.moka._`; only `docs/scala3.md` keeps `*`.
- **Doc snippets can rot**: CI does not run mdoc. `docs/scala2.md` must use plain ` ```scala `
  fences (it cannot compile on Scala 3); other pages use `mdoc` / `mdoc:fail`.
- `.scalafmt.conf` uses the `scala3` dialect except under `src/{main,test}/scala-2/**`; new
  Scala-2-only sources must live under a `scala-2` directory or scalafmt mis-parses them.
- **A tag stages, it does not publish.** `release.yml` sets `CI_SONATYPE_RELEASE: sonaUpload`
  (sbt-ci-release's README still says `sonatypeBundleRelease`; the 1.12.1 jar calls
  `sonaRelease`), so a bundle waits on the Central Portal for a manual Publish; Maven Central
  is immutable. The version comes from a `v`-prefixed tag on a clean tree (sbt-dynver); see
  `RELEASING.md`.
- `IMPROVEMENTS.md` is a local scratch list hidden via `.git/info/exclude`. Never commit it.
