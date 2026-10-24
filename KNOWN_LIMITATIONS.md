# Known limitations

What moka cannot do today, and the test that pins each. A test written as the
behaviour we want is `.ignore`d until it holds; one that asserts a deliberate
error passes. User-facing wording lives in `docs/features.md#known-limitations`
and the version pages.

| ID | Ver. | Limitation | Test | Status |
|---|---|---|---|---|
| L-stale | 3, 2.13 (partly) | After a rename changes, a `generateFields[T]` call in another module than `T` keeps the old literal until recompiled (Scala 3; within one module it propagates). Silent: queries match nothing. 2.13: only empty-package types and the opt-out. | `incremental/src/sbt-test/scala3/other-module` (`pending`); the workaround `scala3/other-module-companion` and the 2.13 claim `scala2/nested-other-file` pass | **open**, see To do |
| L-mutual | 2.13 | Mutually recursive models cannot expand (`StackOverflowError` in the compiler). | `Scala2LimitationsSpec` "L-mutual" (ignored) | accepted |
| L-sibling | 2.13 | A nested type declared in the same object or class as the annottee is invisible to the macro. | `Scala2MokaSpec` "a nested type declared beside the annottee is rejected"; `Scala2ReviewSpec` "sibling shadowing ..." | accepted, clear error |
| L-generic | 2.13 | A generic nested type cannot carry a moka object; only the global opt-out reaches it. | `Scala2CompositionSpec` "a generic nested type cannot be composed ..."; `OptOutSpec` "a generic nested type is read ..." | accepted, clear error |
| L-noflag | 2.13 | `@moka` alone without `-Ymacro-annotations` gives `not found: value Fields`, naming no flag. | `Scala2LimitationsSpec` "L-noflag" (ignored) | accepted, documented |
| L-nowarn | 2.13 | `@nowarn` cannot silence the wrong-format warning (it is issued before the typer); only `-Wconf` can. | `Scala2LimitationsSpec` "L-nowarn" (ignored) | accepted, documented |
| L-samename | 2.13 | A same-named class from another package is descended into. | `Scala2ReviewSpec` "2.5: ..." (ignored) | deferred: the path is correct today |
| L-meta-optout | 2.13 opt-out | A nested type's meta-annotated rename (`@(BsonProperty @field)`) is not read. | `ReviewOptOutSpec` "3b: ..." (ignored) | deferred |
| L-typo | 3 | A typo's error prints the whole generated refinement type. | `Scala3LimitationsSpec` "L-typo" (ignored) | accepted |
| L-semanticdb | 3 | Applying `io.moka.syntax`'s conversion, or interpolating a node, breaks SemanticDB extraction for the file (a warning; Metals loses navigation there). | none: seen in the `MokaSpec` build, not reproduced in `CompileHarness` | accepted, documented |
| L-warn-both | 3 | The wrong-format warning fires even when the companion also generates the other format (2.13 skips it); `@nowarn` silences it. | none | accepted |
| L-runtime | 3 | Each selection is a `Map` lookup per level, not a constant. | none (performance) | accepted, documented |

`CompileHarness` (`examples/src/test/scala-{2,3}`) compiles snippets in-process
and returns every diagnostic, for cases `compileErrors` cannot show: warnings,
and errors after a first macro abort.

## To do before 0.2.0

- [x] **L-stale, red test.** `incremental` scripted tests
      (`sbt "+macros/publishLocal; incremental/scripted"`). Dqo's bug 3 (main
      to test configuration) did not reproduce with sbt 2.0.8.
- [ ] **L-stale, Scala 3 spike.** Make the expansion record a dependency on
      `T`'s members, so the consumer module is invalidated when a rename
      changes across modules. If it works, `scala3/other-module` becomes a
      `test`; if not, the companion-val workaround stays the documented answer.
- [ ] Re-test the snapshot on the real projects.
- [ ] After merge: tag `v0.2.0` (`RELEASING.md`), date the changelog heading,
      publish gh-pages from the tagged checkout.
