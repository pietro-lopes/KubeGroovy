# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project overview

KubeGroovy is a NeoForge mod for Minecraft that replaces KubeJS's JavaScript
scripting with sandboxed Apache Groovy scripts.

- Minecraft: 26.1.2
- NeoForge: 26.1.2.84
- Java: toolchain 25
- Groovy: version in `gradle.properties`, bundled into the mod jar
- Package root: `dev.uncandango.kubegroovy`
- License: LGPL-3.0

Treat the public script API and addon-facing Java API as compatibility-sensitive.
Event names, bindings, resource identifiers, plugin hooks, and injected interfaces
may be consumed outside this repository.

## Repository map

- `src/main/java/dev/uncandango/kubegroovy/` contains the mod implementation.
- `script/` and `script/groovy/` contain script lifecycle, compilation, class
  loading, and sandbox enforcement.
- `plugin/` contains plugin discovery, bindings, type wrappers, and class-filter
  configuration.
- `core/mixin/` and `src/main/resources/kubegroovy.mixins.json` contain Minecraft
  injections. Keep mixin classes and configuration entries in sync.
- `interfaces.json` defines NeoForge interface injection. Keep it synchronized
  with the injected `*KGY` interfaces.
- `src/main/resources/` contains mod metadata, access transformers, plugin and
  class-filter lists, assets, and data resources.
- `src/test/` contains the NeoForge game-test mod, Groovy fixture scripts, and
  focused JUnit coverage.

## Build and run commands

Use the checked-in Gradle wrapper. On Windows use `gradlew.bat`; on Unix-like
systems use `./gradlew`.

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat runClient_jei
.\gradlew.bat runClient_rei
.\gradlew.bat test
.\gradlew.bat runGametest
.\gradlew.bat coverageSummaryJson
```

`build` is the baseline verification for changes to code, resources, or build
configuration. The run tasks are manual checks; use only the environment relevant
to the change. CI also builds with Java 25.

## Test harness

AlmostGradle wires the test mod, NeoForge test framework, JUnit, and AssertJ. The
headless suite is available through `runGametest`; the task synchronizes Groovy
fixtures from `src/test/kubegroovy` before launching the server. `test` runs the
focused JVM regressions, and `coverageSummaryJson` merges JaCoCo data from both
test JVMs into `build/reports/coverage`.

For runtime, event, scripting, or sandbox changes, run `test`, `runGametest`, and
`build`. Do not claim the game-test suite passed unless the task reports all 51
holders successful and no script load errors.

## Working rules

- Follow `.editorconfig` and the style of the surrounding file. Java sources use
  tabs, and package-level `@NullMarked` declarations are the default; use
  `@NullUnmarked` only where the surrounding API requires it.
- Keep project, Minecraft, NeoForge, Groovy, and dependency versions centralized in
  `gradle.properties`; do not duplicate version literals without a build-system
  reason.
- Preserve script and addon compatibility unless the requested change explicitly
  allows a breaking change. Update examples and documentation when public behavior
  changes.
- Treat changes to the sandbox, class filters, compilation customizers, reflection
  controls, and class loading as security-sensitive. Never relax a restriction
  without focused regression coverage and an explanation of the intended exposure.
- When changing mixins or interface injection, verify both the Java implementation
  and its resource/configuration declaration.
- Do not edit generated or local runtime output under `build/`, `.gradle/`, `run/`,
  `runs/`, `logs/`, or IDE metadata such as `.idea/`.
- Keep changes narrowly scoped and preserve unrelated work already present in the
  working tree.

## Completion checks

- Review the final diff for unintended compatibility or resource-registration
  changes.
- Run `build` for code, resource, or build configuration changes.
- State which manual runs or tests were performed and disclose the current game-test
  limitation when it affects confidence in the change.
