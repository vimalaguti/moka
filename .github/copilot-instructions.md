# Copilot instructions for moka

## Build, test, and formatting commands
- Full compile (all modules): `sbt compile`
- Full test suite (CI command): `sbt test`
- Run a single test suite: `sbt "core/testOnly io.moka.MokaSpec"`
- Run the sample app in `core`: `sbt run` (aliased to `core/run` in `build.sbt`)
- Build documentation content from sbt docs project: `sbt docs/mdoc`
- Run website locally (from `website/`): `yarn start`

## High-level architecture
- This is a multi-module sbt build with `root`, `core`, `macros`, and `docs` projects.
- `macros` contains the macro annotation implementation (`io.moka.Moka.moka`) in `macros/src/main/scala-2.13/io/moka/Moka.scala`; it generates an object (default name `Fields`) inside a case class companion.
- `core` depends on `macros` and contains executable/demo code plus tests (`core/src/test/scala/io/moka/MokaSpec.scala`) that define the supported annotation behavior.
- `docs` (project rooted at `moka-docs`) is wired with `MdocPlugin`, `DocusaurusPlugin`, and `GhpagesPlugin`; authored docs live in `docs/`, and Docusaurus site assets live in `website/`.

## Key repository-specific conventions
- Prefer annotating case classes with `@moka` from `io.moka.Moka.moka`; generated accessors are expected under `Companion.Fields.<param>` unless a custom object name is explicitly requested.
- BSON name mapping is part of the supported contract: `@BsonProperty("...")` (Mongo Scala driver) and `@bsonField("...")` (zio-bson) must drive generated field-name values.
- Behavior expectations are test-driven in `MokaSpec`; when changing macro behavior, update/add cases there first and keep existing companion-object compatibility expectations intact.
- The codebase targets Scala 2.13 with macro annotations enabled (`-Ymacro-annotations`) in relevant modules; keep macro-related changes compatible with this setup.
