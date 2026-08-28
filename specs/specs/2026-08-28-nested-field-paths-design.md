# Nested field paths — design

Date: 2026-08-28 · Status: proposed

> This document records a decision and the reasoning behind it. It is **not** a
> description of current behaviour — read the code for that.

## Part A — What changes

Today every generated member is a literal `String`, so moka can name a field but
cannot name a field *inside* a field. MongoDB addresses sub-documents with dotted
paths (`"a.b"`), and that is currently unreachable.

After this change a generated member is one of two shapes:

- **Leaf** — a literal `String`, exactly as today, for any field whose type is not
  descendable.
- **Node** — a `FieldPath[P <: String]` carrying its own path in `P` and exposing
  one member per field of the nested type, for any field whose type is descendable.

```
case class Second(b: Int)
case class First(a: Second, n: Int)

First.Fields.a.b     : "a.b"
First.Fields.a.path  : "a"
First.Fields.n       : "n"     // unchanged
```

Rules that must hold:

1. **Descendable** means a case class, or `Option[X]` / `Iterable[X]` where `X` is
   descendable. `Option` and collections are *transparent*: they contribute nothing
   to the path, because MongoDB's dot notation does not distinguish them. `Map` is
   out of scope.
2. **Not descendable** is everything else, explicitly including value classes
   (`extends AnyVal`), which stay leaves.
3. **Path composition** — a child's path is the parent's path, a `.`, and the
   child's bson name. `@BsonProperty` / `@bsonField` apply at every level, not just
   the top.
4. **Literal types everywhere** — leaf members and `node.path` both have literal
   singleton types, on both Scala versions.
5. **Cycles terminate** — a field whose type already appears on the current path is
   emitted as a leaf rather than descended into.
6. **Cross-version parity** — identical source produces identical types and values
   on 2.13 and 3, subject to rule 7.
7. **Scala 2 constraint** — the `@moka` annotation macro runs before the typer and
   cannot resolve a type declared as a *member of the same enclosing object or
   class* as the annotated case class. Such a field aborts compilation with a
   diagnostic naming the type and the constraint. Top-level types in the same file,
   and types in any other file, resolve normally. Scala 3 has no such constraint.
8. **Node to String is explicit by default.** `node.path` is the API everywhere.
   The opt-in `import io.moka.syntax._` — the same line on both Scala versions —
   additionally lets a node stand in for its own path where a `String` is expected.
   The conversion is never in implicit scope by default.
9. **Surface** — `generateFields[T]` yields the root, refined with `T`'s members.
   The root deliberately carries **no** `path` member, so a top-level field named
   `path` cannot collide with the accessor. `FieldPath` is reachable through the
   existing single `import io.moka.*`.

Consequence to absorb: the repo's own `Definitions.scala` declares its fixtures as
members of `object Definitions`, which rule 7 forbids for nested models. Those
fixtures move.

### Delivery

Three stages, each independently green: (0) the fixture move rule 7 forces, no
behaviour change; (1) descent into plain nested case classes on both versions,
with cycle detection; (2) `Option` / `Iterable` transparency. Within stage 1 the
Scala 2 macro goes first — it is the constrained side, and a surprise there
reshapes the shared contract.

`FieldPath` is a trait carrying only `def path: P`. On Scala 2 the generated
nested objects extend it directly and the compiler folds every selection to a
constant; on Scala 3 it is the shared supertype of the concrete `PathNode` the
macro instantiates. The opt-in conversion is defined against the trait, once per
version.

## Part B — Record

### Why a new type at all

`Fields.a` cannot be both the `String` `"a"` and a namespace containing `.b`.
`String` is final and is not `Selectable`, so no refinement can add members to a
value typed `"a"`. Any design that keeps `Fields.a` a `String` cannot support
`Fields.a.b`. The path literal therefore moves out of the value's own type and into
a type parameter: `FieldPath["a"]` instead of `"a"`.

### Hybrid rather than uniform node typing

Considered making *every* member a `FieldPath`, for predictability: a field's static
type would then not depend on whether its type happens to be a case class.

Rejected. The implicit conversion that bridges `FieldPath` to `String` fires only in
expected-type positions. In inference positions it silently produces the wrong type
rather than an error:

```
Map(Fields.a -> 1)        // Map[FieldPath[...], Int], compiles, wrong
List(Fields.a, Fields.b)  // List[FieldPath[?]], breaks a later varargs splat
```

Under uniform typing that footgun applies to every field in every model. Under
hybrid it applies only to nodes, which are the minority and are usually descended
off immediately (landing back on a `String`). The accepted cost is that changing a
field's type from `Int` to a case class silently changes that member's static type —
benign at Mongo call sites, which keep compiling through the conversion.

### Rejected syntaxes

- **Flat backtick members** — ``Fields.`a.b` ``. Cheapest possible: no new type, no
  conversion, everything stays a literal `String`. Rejected on ergonomics; the
  backticks appear at every nested call site, and the flat namespace grows
  combinatorially with model depth.
- **Lambda path macro** — `Fields.path(_.a.b)`. Fully typed, needs no `Selectable`,
  and sidesteps the Scala 2 constraint entirely because it expands after the typer.
  Rejected because it is a second, parallel API rather than an extension of the
  existing one, and because the Scala 2 probe showed the constraint is narrow enough
  not to justify it. Worth revisiting if rule 7 proves too painful in practice.
- **Making the node a subtype of `String`** — impossible, `String` is final.

### Probe findings (2026-08-28, throwaway code, not retained)

Scala 3 — the mechanism compiles as designed: chained structural selection through
nested `Refinement`s preserves literal types, `node.path` keeps its own literal, and
a hybrid leaf stays a plain literal `String`.

Scala 2 — real nested objects extending `FieldPath` give the same shape with no
structural types and no runtime reflection, so member access stays a static
selection.

Scala 2 type discovery via `c.typecheck` inside the annotation macro, which decides
whether the feature is reachable at all on 2.13:

| Nested type declared | Resolves |
|---|---|
| Member of the same enclosing object as the annottee | **no** — both before and after the annottee |
| Top level in the same file | yes |
| In another file (including `@moka`-annotated) | yes |
| `Option[X]` / `List[X]` type arguments | yes, with case-accessor detail |

The boundary is the enclosing owner, not the file, and declaration order is
irrelevant. This is what makes rule 7 narrow enough to live with: ordinary model
code puts case classes at package level, not nested inside a shared wrapper object.

### Abort rather than degrade, on unresolvable Scala 2 types

When rule 7 is hit the macro could instead warn and emit a leaf. Rejected: that
produces a *silent divergence* between 2.13 and 3 for the same source, which is the
one failure mode this library exists to prevent. The user would see a confusing
"value b is not a member of String" at a distant call site on 2.13 only.

Accepted risk: a field whose type is unresolvable for an unrelated reason — a type
alias declared as a sibling member, say — aborts even though it is a leaf. Judged
rare; the diagnostic must be explicit enough to point at the fix.

### The conversion is opt-in, and is an `implicit def`

Two findings, both measured, shaped this.

**It must be an `implicit def`, not a `given Conversion`.** A wildcard import does
not bring `given` instances into scope on Scala 3 — that needs
`import io.moka.syntax.given`, which Scala 2 cannot parse. There is then no single
import line that works in cross-compiled sources, which defeats the point of the
library. An old-style `implicit def` is imported by `import io.moka.syntax._` on
both versions.

**The feature-warning argument for opt-in was wrong.** An earlier probe found that
applying a `given Conversion` on Scala 3 warns at the use site unless the consumer
adds `import scala.language.implicitConversions`, and that asymmetry was the stated
reason to keep the conversion out of implicit scope. Re-measured with an
`implicit def`: no feature warning on either version. That argument no longer holds.

It stays opt-in for the surviving reason — the conversion fires only in
expected-type positions, so in inference positions it silently produces the wrong
type rather than an error (`Map(Fields.a -> 1)` infers a `Map` keyed by the node).
Keeping it out of implicit scope means that mistake is a compile error by default,
and only the people who ask for the sugar take on the footgun.

### The root is not a `FieldPath`

Making the root a `FieldPath[""]` would have been uniform, and was the original
intent. Rejected during implementation: it puts a `path` member on *every*
generated `Fields` object, so any case class with a top-level field named `path`
stops compiling. A nested case class with a `path` field is a rare edge; a
top-level one is not — `path` is an ordinary document field name. Keeping the root
free of it confines the collision to nested types, and costs only an internal
asymmetry (`FieldNames` as the Scala 3 root type, a plain object on Scala 2) that
never appears in user code.

### Compile-time cost, measured

The residual risk below worried about refinement growth. Measured with
`scripts/bench-compile.sh` on synthetic models (width 8, varying depth and
branching), clean compile of a single generated file, semanticdb off:

| members | Scala 2.13.18 | Scala 3.3.8 |
|---|---|---|
| 8 | 5 s | 6 s |
| 44 | 6 s | 8 s |
| 116 | 8 s | 9 s |
| 359 | 11 s | 12 s |
| 1088 | 15 s | 17 s |

About 5-6 s of each figure is fixed overhead, so the marginal cost is roughly
linear in member count at ~10 ms per member, and Scala 2 — which emits real
nested objects — is consistently slightly *faster* than Scala 3's refinement
chain. Growth is not the quadratic blow-up the risk anticipated.

This is what makes the array hop affordable. Exposing descendable `matched`
(`$`) and `all` (`$[]`) nodes means emitting each element subtree three times,
and tripling a realistic model's member count costs a few seconds of marginal
compile time, not minutes.

### Runtime cost, measured

The open question `javap` could not answer: since `Fields` is a stable val
holding a small immutable `Map` keyed by compile-time constants, would the JIT
inline and fold the whole chain away? JMH says no. Average time per access,
`bench-jmh`, one fork, 5x0.5s warmup and measurement:

| | Scala 2.13.18 | Scala 3.3.8 |
|---|---|---|
| baseline (a literal) | 0.63 ns | 0.58 ns |
| 1 hop | 0.59 ns | 1.68 ns |
| 2 hops | 0.93 ns | 5.80 ns |
| 3 hops | 0.59 ns | 8.44 ns |

Scala 2 is indistinguishable from the baseline at every depth, which confirms
the constant folding seen in the bytecode — the paths genuinely cost nothing.
Scala 3 pays roughly 2.6 ns per additional hop and does not fold, so C2 does not
see through `selectDynamic` even on a stable receiver with constant keys.

Eight nanoseconds for a three-level path, against a network round trip of
roughly a million, is not a reason to change the design. It does mean the array
hop's extra segment (`items.$.qty` rather than `items.qty`) costs about 2.6 ns
on Scala 3 and nothing on Scala 2 — still immaterial.

### Accepted residual risks

- A nested case class with a field literally named `path` collides with the accessor.
  It fails loudly at compile time on both versions (the refinement cannot conform),
  so it is not a correctness hazard. A configurable accessor name is deferred until
  someone hits it.
- **The conversion breaks SemanticDB extraction on Scala 3.** In any file that
  applies it to a node, the compiler reports `Internal error in extracting
  SemanticDB ... Ignoring <field> of symbol class FieldNames` and emits no index
  for that file — Metals then loses go-to-definition and find-references there.
  Compilation itself succeeds; it is a warning. Verified by removing the single
  test that uses the conversion, which makes it disappear. It is a bug in the
  SemanticDB extractor's handling of a synthetic conversion applied to a deeply
  refined type, not something moka can fix. Scala 2 is unaffected. This is a
  further reason the conversion is opt-in rather than always in scope.
- **Mutually recursive models break the Scala 2 compiler.** `@moka case class A(b: B)`
  with `case class B(back: A)` makes `c.typecheck(B)` force `B`'s info, which
  references `A`, which re-enters `A`'s own in-progress annotation expansion —
  `StackOverflowError`, not a diagnostic. A *direct* self-reference is fine: the
  macro recognises the annottee's own name syntactically and emits a leaf without
  typechecking it. Indirect cycles cannot be caught the same way, because the
  overflow happens inside `c.typecheck` before the macro regains control. Scala 3
  handles both. Judged acceptable: mutually recursive document models are rare, and
  the failure is at compile time.
- Refinement count grows with the product of field counts along each path. Cycle
  detection guarantees termination but not a small type. A depth cap is the
  implementation's safety valve if compile times degrade; no user-facing knob.
