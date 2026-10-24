# Known limitations

What moka cannot do today, and the test that pins each. A test written as the
behaviour we want is `.ignore`d until it holds; one that asserts a deliberate
error passes. User-facing wording lives in `docs/features.md#known-limitations`
and the version pages.

| ID | Ver. | Limitation | Test | Status |
|---|---|---|---|---|
| L-stale | 3, 2.13 | After a rename changes, code in another file than the renamed type keeps the old literal until recompiled, in the same or another module: an outer type nesting it, or a `generateFields[T]` call outside `T`'s file. A rename is not an API change for zinc. Silent: queries match nothing. On 2.13 the composed `N.Obj.member` reference does not change that. | `incremental/src/sbt-test/`: `scala3/nested-other-file`, `scala3/other-file`, `scala3/other-module`, `scala2/nested-other-file` (all `pending`); `scala3/other-module-companion` passes (a val in `T`'s companion is current) | accepted for 0.2.0: clean build after a rename (documented); fix tracked in issue #24 |
| L-mutual | 2.13 | Mutually recursive models cannot expand. | `Scala2CompositionSpec` "a mutually recursive pair is a clear error, not a StackOverflowError" | accepted, clear error |
| L-sibling | 2.13 | A nested type declared in the same object or class as the annottee is invisible to the macro. | `Scala2MokaSpec` "a nested type declared beside the annottee is rejected"; `Scala2ReviewSpec` "sibling shadowing ..." | accepted, clear error |
| L-generic | 2.13 | A generic nested type cannot carry a moka object; only the global opt-out reaches it. | `Scala2CompositionSpec` "a generic nested type cannot be composed ..."; `OptOutSpec` "a generic nested type is read ..." | accepted, clear error |
| L-noflag | 2.13 | `@moka` alone without `-Ymacro-annotations` gives `not found: value Fields`, naming no flag. | `Scala2LimitationsSpec` "L-noflag" (ignored) | accepted, documented |
| L-samename | 2.13 | A same-named class from another package is descended into. | `Scala2ReviewSpec` "2.5: ..." (ignored) | deferred: the path is correct today |
| L-typo | 3 | A typo's error prints the whole generated refinement type. | `Scala3LimitationsSpec` "L-typo" (ignored) | accepted |
| L-semanticdb | 3 | Applying `io.moka.syntax`'s conversion, or interpolating a node, breaks SemanticDB extraction for the file (a warning; Metals loses navigation there). | none: seen in the `MokaSpec` build, not reproduced in `CompileHarness` | accepted, documented |
| L-warn-both | 3 | The wrong-format warning fires even when the companion also generates the other format (2.13 skips it); `@nowarn` silences it. | none | accepted |
| L-runtime | 3 | Each selection is a `Map` lookup per level, not a constant. | none (performance) | accepted, documented |

`CompileHarness` (`examples/src/test/scala-{2,3}`) compiles snippets in-process
and returns every diagnostic, for cases `compileErrors` cannot show: warnings,
and errors after a first macro abort.

## To do before 0.2.0

- [x] **L-stale, red test.** `incremental` scripted tests
      (`sbt "+macros/publishLocal; incremental/scripted"`), with zinc's
      recompile-everything threshold off so small builds show real decisions.
      Dqo's bug 3 (rename in `main`, stale in `test`) did not reproduce.
- [x] **L-stale, call-site spike** (failed). Dead selections of every read
      field in the Scala 3 expansion change nothing: zinc's debug log shows no
      API change at all for the renamed type, so no use of it can invalidate.
- [x] **L-stale, decide.** For 0.2.0 a clean build after a rename is the
      documented rule; the redesign it would take is issue #24.
- [ ] Re-test the snapshot on the real projects.
- [ ] After merge: tag `v0.2.0` (`RELEASING.md`), date the changelog heading,
      publish gh-pages from the tagged checkout.
