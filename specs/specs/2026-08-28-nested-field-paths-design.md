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
8. **Node to String is explicit.** `node.path` is the API; there is no implicit
   bridge. If a conversion is ever added it must be an opt-in import and never the
   `FieldPath` companion — see the record below for why.
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

The implicit conversion is deferred out of v1 entirely. It is sugar; `.path`
covers every call site without it, and deferring removes the cross-version
asymmetry described below from the first release.

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

### The conversion is opt-in, and why

Scala 2 applies an implicit conversion at the use site silently. Scala 3 emits a
feature warning at every use site unless the consumer adds
`import scala.language.implicitConversions`. Putting the conversion in the
`FieldPath` companion — where it would always be in implicit scope — therefore makes
the default experience *asymmetric across versions*, which contradicts the library's
premise. Moving it to an explicit `io.moka.syntax` import makes both versions
behave the same and leaves the default path warning-free.

### The root is not a `FieldPath`

Making the root a `FieldPath[""]` would have been uniform, and was the original
intent. Rejected during implementation: it puts a `path` member on *every*
generated `Fields` object, so any case class with a top-level field named `path`
stops compiling. A nested case class with a `path` field is a rare edge; a
top-level one is not — `path` is an ordinary document field name. Keeping the root
free of it confines the collision to nested types, and costs only an internal
asymmetry (`FieldNames` as the Scala 3 root type, a plain object on Scala 2) that
never appears in user code.

### Accepted residual risks

- A nested case class with a field literally named `path` collides with the accessor.
  It fails loudly at compile time on both versions (the refinement cannot conform),
  so it is not a correctness hazard. A configurable accessor name is deferred until
  someone hits it.
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
