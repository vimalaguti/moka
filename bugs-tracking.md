# Bugs & Fixes Tracking

This document tracks known issues identified during review, their root cause analysis, test reproductions, proposed solutions, and current progress.

---

## Status Overview

| ID | Severity | Summary | Status |
|---|---|---|---|
| **BUG-1** | High | Scala 3 never reads Java annotations (e.g. Java driver's `@BsonProperty`) | Fixed |
| **BUG-2** | High | Scala 2 loses Java annotations on nested types (`bsonNameFromSymbol`) | Fixed |
| **BUG-3** | Medium | Scala 2 ignores named arguments like `@BsonProperty(value = "x")` at top level | Identified / Solution Proposed |
| **BUG-4** | Medium | Test suite lacks real Java annotation test cases | Identified / Solution Proposed |
| **BUG-5** | Low | Scala 2 nested conflict test fails on inner class rather than exercising nested check | Identified / Solution Proposed |
| **BUG-6** | Low | Conflicts on bytecode symbols report at `NoPosition` without file/line info | Identified / Solution Proposed |
| **BUG-7** | Low | Stacking `@moka` and `@mokaBson` with placeholders fails with "BsonFields is already defined" | Identified / Solution Proposed |

---

## Detailed Bug Descriptions & Proposed Solutions

### BUG-1 (High): Scala 3 never reads Java annotations

* **Affected File**: `macros/src/main/scala-3/io/moka/Moka.scala` (line 57)
* **Problem Description**:
  The AST pattern in Scala 3's `bsonName` matcher is:
  ```scala
  case ann @ Apply(_, List(Literal(StringConstant(value)))) =>
    (ann.tpe.typeSymbol.name, value)
  ```
  Java annotations (such as Java driver's `org.bson.codecs.pojo.annotations.BsonProperty`) define methods with defaults (e.g. `String value()`, `boolean useDiscriminator() default false`).
  In Scala 3 Tasty AST:
  - Positional syntax `@BsonProperty("x")` is compiled as:
    `Apply(fun, List(Literal(StringConstant("x")), Wildcard()))`
  - Named syntax `@BsonProperty(value = "x")` is compiled as:
    `Apply(fun, List(NamedArg("value", Literal(StringConstant("x"))), Wildcard()))`
  Because the argument list contains a `Wildcard()` for the default parameter or uses `NamedArg`, it never matches `List(Literal(StringConstant(value)))`. Scala 3 silently ignores the Java annotation and falls back to the plain Scala field name.
* **Reproduction**:
  Annotating a case class field with `@org.bson.codecs.pojo.annotations.BsonProperty("custom_name")` in Scala 3 produces `"field"` instead of `"custom_name"`.
* **Proposed Solution**:
  Extract the string value by matching against either positional literals or `NamedArg` elements within `Apply(_, args)`:
  ```scala
  def extractAnnotationString(tree: Term): Option[(String, String)] = {
    val annName = tree.tpe.typeSymbol.name
    tree match {
      case Apply(_, args) =>
        val valueOpt = args.collectFirst {
          case Literal(StringConstant(v)) => v
          case NamedArg("value" | "key" | "name", Literal(StringConstant(v))) => v
          case NamedArg(_, Literal(StringConstant(v))) => v
        }
        valueOpt.map(v => (annName, v))
      case _ => None
    }
  }
  ```

---

### BUG-2 (High): Scala 2 loses Java annotations on nested types

* **Affected File**: `macros/src/main/scala-2/io/moka/Moka.scala` (line 183)
* **Problem Description**:
  In `bsonNameFromSymbol`, nested types have their constructor parameter symbols inspected:
  ```scala
  ann.tree.children.collectFirst { case Literal(Constant(v: String)) =>
    (name, v)
  }
  ```
  In scalac, annotations on typed `Symbol`s wrap arguments in `AssignOrNamedArg(Ident("value"), Literal(Constant(v: String)))`, or store them in `ann.javaArgs: Map[Name, JavaArgument]`.
  Because `Literal` is not a direct child of `ann.tree` (it is inside `AssignOrNamedArg`), `collectFirst` misses it. Furthermore, for symbols loaded from class files, arguments are stored in `ann.javaArgs`.
* **Reproduction**:
  ```scala
  case class S2JavaInner(@org.bson.codecs.pojo.annotations.BsonProperty("inner_renamed") f: String)
  @moka case class JavaOuter(inner: S2JavaInner)
  ```
  On Scala 2, `JavaOuter.Fields.inner.f` returns `"inner.f"` instead of `"inner.inner_renamed"`.
* **Proposed Solution**:
  In `bsonNameFromSymbol`, inspect `AssignOrNamedArg` in `ann.tree`, search recursive descendants if needed, and fall back to `ann.javaArgs`:
  ```scala
  val fromTree = ann.tree match {
    case Apply(_, args) =>
      args.collectFirst {
        case Literal(Constant(v: String)) => v
        case AssignOrNamedArg(_, Literal(Constant(v: String))) => v
      }
    case other =>
      other.collect { case Literal(Constant(v: String)) => v }.headOption
  }
  val fromJavaArgs = ann.javaArgs.values.collectFirst {
    case LiteralArgument(Constant(v: String)) => v
  }
  fromTree.orElse(fromJavaArgs).map(v => (name, v))
  ```

---

### BUG-3 (Medium): Scala 2 ignores named arguments at top level

* **Affected File**: `macros/src/main/scala-2/io/moka/Moka.scala` (line 165)
* **Problem Description**:
  In `bsonNameFromMods`:
  ```scala
  case Apply(
        Select(New(tpt), _),
        Literal(Constant(v: String)) :: Nil
      ) if leafTypeName(tpt).isDefined =>
    (leafTypeName(tpt).get, v)
  ```
  When a user writes `@BsonProperty(value = "x")` or `@BsonProperty(key = "x")`, scalac constructs an `AssignOrNamedArg(Ident(...), Literal(Constant(...)))`. This fails the `Literal(Constant(...)) :: Nil` pattern, and the annotation is silently ignored.
* **Reproduction**:
  ```scala
  @moka case class Top(@BsonProperty(value = "renamed") f: String)
  ```
  Produces `Top.Fields.f == "f"` instead of `"renamed"`.
* **Proposed Solution**:
  Support `AssignOrNamedArg` in `bsonNameFromMods`:
  ```scala
  val extracted = mods.annotations.flatMap {
    case Apply(Select(New(tpt), _), args) if leafTypeName(tpt).isDefined =>
      val name = leafTypeName(tpt).get
      val vOpt = args.collectFirst {
        case Literal(Constant(v: String)) => v
        case AssignOrNamedArg(_, Literal(Constant(v: String))) => v
      }
      vOpt.map(v => (name, v))
    case _ => None
  }
  ```

---

### BUG-4 (Medium): Test suite lacks real Java annotation test cases

* **Affected File**: `examples/src/test/scala*`
* **Problem Description**:
  Tests previously relied on synthetic Scala classes like `class JsonProperty(...) extends StaticAnnotation`. Because Scala case/static annotations do not exhibit Java annotation AST peculiarities (`NamedArg`, trailing default `Wildcard()`, `AssignOrNamedArg`, `ann.javaArgs`), tests passed while real Java driver annotations failed.
* **Proposed Solution**:
  Use the Java driver's `org.bson.codecs.pojo.annotations.BsonProperty` (available transitively via `mongo-scala-bson`) in cross-version test specs:
  - Top-level with positional argument: `@BsonProperty("name")`
  - Top-level with named argument: `@BsonProperty(value = "name")`
  - Nested with positional argument
  - Nested with named argument

---

### BUG-5 (Low): Scala 2 nested conflict test doesn't test nesting

* **Affected File**: `examples/src/test/scala-2/io/moka/Scala2ConflictSpec.scala` (lines 92-103)
* **Problem Description**:
  ```scala
  object NestedConflict {
    @moka case class InnerBad(
      @org.mongodb.scala.bson.annotations.BsonProperty("b1")
      @zio.bson.bsonField("b2")
      name: String
    )
    @moka case class OuterBad(inner: InnerBad)
  }
  ```
  1. `InnerBad` is annotated with `@moka`, so it aborts compilation itself before `OuterBad` is expanded.
  2. In Scala 2, `@moka` cannot resolve a sibling type defined inside the same object before typer (`"moka cannot resolve type 'InnerBad' of field 'inner'"`).
  Therefore, the nested conflict check was never actually executed by `OuterBad`.
* **Proposed Solution**:
  Define `S2NestedConflictInner` without `@moka` at package level (or in `Scala2Definitions.scala`):
  ```scala
  case class S2NestedConflictInner(
    @org.mongodb.scala.bson.annotations.BsonProperty("b1")
    @zio.bson.bsonField("b2")
    name: String
  )
  ```
  Then test `OuterBad(inner: S2NestedConflictInner)` annotated with `@moka`, verifying that `OuterBad` fails with the expected conflict message.

---

### BUG-6 (Low): Conflicts on bytecode symbols report at `NoPosition`

* **Affected File**: `macros/src/main/scala-2/io/moka/Moka.scala` (line 187)
* **Problem Description**:
  When a symbol `sym` is loaded from an external jar or previous compilation run, `sym.pos` is `NoPosition`.
  Calling `c.abort(sym.pos, msg)` prints an error without source file or line context (e.g. `<macro>: error:`), which is confusing to users.
* **Proposed Solution**:
  In `bsonNameFromSymbol`, fallback to `c.enclosingPosition` if `sym.pos == NoPosition`:
  ```scala
  val pos = if (sym.pos != NoPosition) sym.pos else c.enclosingPosition
  resolveName(pos, fallback, extracted, mode)
  ```

---

### BUG-7 (Low): Stacking `@moka @mokaBson` with placeholders fails with "BsonFields is already defined"

* **Affected File**: `macros/src/main/scala-2/io/moka/Moka.scala` (lines 367-383)
* **Problem Description**:
  When `@moka` and `@mokaBson` are stacked:
  1. `@moka` runs first and replaces all placeholders (`val Fields = ...`, `val BsonFields = ...`) with `object Fields` and `object BsonFields`.
  2. `@mokaBson` runs next. Because `val BsonFields` was already transformed into `object BsonFields`, `replacedPlaceholder` is `false`.
  3. The `else` branch executes: `q"object $objectName { ..$terms }" +: stats`, prepending a second `object BsonFields` to the companion.
  4. Scalac fails with `BsonFields is already defined as object BsonFields`.
* **Proposed Solution**:
  Check if `object $objectName` (or any member with that name) is already defined in `stats` before generating the default object:
  ```scala
  val alreadyDefined = stats.exists {
    case q"$_ object $name extends ..$_ { $_ => ..$_ }" => name.toString == objectName
    case q"$_ val $name: $_ = $_" => name.toString == objectName
    case _ => false
  }
  val newStats =
    if (replacedPlaceholder) updatedStats
    else if (alreadyDefined) stats
    else {
      val terms = generateFieldNames(className, fields.head, defaultMode)
      q"object $objectName { ..$terms }" +: stats
    }
  ```
  Alternatively, abort with a clean macro error if an intentional conflict/stacking restriction is violated.

---

## Execution Plan

1. **Step 1 (Red Tests for Java Annotations)**:
   - Add test cases in Scala 2 and Scala 3 test suites using real Java driver's `@org.bson.codecs.pojo.annotations.BsonProperty` (positional and named, top-level and nested).
2. **Step 2 (Fix BUG-1, BUG-2, BUG-3)**:
   - Implement argument extraction improvements in `macros/src/main/scala-3/io/moka/Moka.scala` and `macros/src/main/scala-2/io/moka/Moka.scala`.
   - Verify all tests pass.
3. **Step 3 (Fix BUG-5 & BUG-6)**:
   - Move nested conflict test model in Scala 2 to package level without `@moka`.
   - Guard `sym.pos` with fallback to `c.enclosingPosition`.
4. **Step 4 (Fix BUG-7)**:
   - Guard against duplicate companion object emission in Scala 2 macro.
   - Add a test verifying stacking behavior / clean error.
5. **Step 5 (Format & Verify All Versions)**:
   - Run scalafmt and full cross-version tests (`sbt +testFull`, `sbt docs/mdoc`).
