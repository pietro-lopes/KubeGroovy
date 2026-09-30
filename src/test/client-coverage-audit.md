# Client coverage audit

Measured on 2026-09-30 with `coverageClientSummaryJson -x test`, after
refreshing the report from the existing execution data. This report combines
JUnit and the client GameTest JVM, including its integrated server; it does
not include the dedicated-server execution data.

Baseline: 47.09% instructions, 35.23% branches, 48.40% lines.
Packages below are ordered by missed instructions, rather than percentage,
to identify the largest opportunities.

| Package under `dev.uncandango.kubegroovy` | Instruction coverage | Missed instructions | Script reachability |
| --- | ---: | ---: | --- |
| `core` | 11.52% | 6,436 | Injected entity, player, inventory, item, level, and client helpers are directly reachable. Mutations need Java readback of the affected object. Hidden hooks and `NoMixinException` defaults need their real engine paths. |
| `typing` | 44.53% | 5,214 | Ordinary scripts supply types and bindings, but do not exercise all generator/export branches. Dedicated Java generator/catalog tests are more appropriate for reflection and file output. |
| `recipe.component` | 39.33% | 3,944 | Recipe schema and recipe events reach wrapping, matching, and codec paths. Custom components require matching schema fixtures; emitted recipes should be checked in the live recipe manager. |
| `plugin.builtin.wrapper` | 53.03% | 3,830 | `$Item`, `$Ingredient`, `$Text`, `$NBT`, and other bindings expose many branches. Check converted values and independently verify any retained item/world changes. |
| `command` | 16.76% | 3,437 | Java command sources can invoke registered Groovy commands and built-in commands. Permission, diagnostics, reload, and export paths require specific command setups; exports mutate files. |
| `util` | 54.67% | 3,299 | Public bindings reach some utilities; engine helpers and IO/error branches also need Java tests. IO fixtures must use isolated paths and verify content and cleanup. |
| `recipe` | 42.54% | 3,256 | Recipe events can add, remove, and query recipes. Existing `coverage_recipes` checks the resulting manager from Java. |
| `script.groovy` | 34.13% | 2,804 | Normal scripts cover successful compilation and dispatch. Invalid/sandbox-denied source belongs in isolated Java regressions so it does not prevent the client world from loading. |
| `script` | 45.76% | 2,697 | Lifecycle events cover normal execution; reload, errors, and export branches need dedicated lifecycle setups. |
| `client` | 66.18% | 2,220 | Client events, assets, and wrappers are script-accessible. The largest individual gaps are error-screen UI, which a successful world boot does not reach. Input and rendering paths need appropriate client state. |

## Added coverage and side effects

- `coverage_core_mutations` invokes a Groovy command on an isolated GameTest
  mock player. Java immediately verifies selected slot, food, saturation, XP,
  health and speed attributes, rotation, all motion components, persistent
  Forge persistent data, main/offhand items, custom-name components, helmet,
  and carried item. The script names `getForgePersistentData()` explicitly
  because the ambiguous `persistentData` property selected different stores
  between the dedicated and integrated runs during this audit.
  Immediate readback prevents a later tick from changing the observed values.
  Script assertions also check slot clamping, modifier replacement, stack-copy
  isolation, and the zero-count branch.
- `client.core.inputGuards` exercises client world lookup and input fallback
  branches without requiring physical input. The existing Java lifecycle
  holder verifies its success marker. These calls do not mutate client state.
- The client wrapper script registers `coverage_seed_conditional`; the new
  `client_model_callback_effects` Java holder verifies true and false callback
  results and a missing callback. The registry entry is retained only in the
  test client JVM.

The command fixture exposed `IncompatibleClassChangeError` when item naming
called the component-holder `kgy$self()` bridge. A concrete override in the
existing `ItemStackMixin` resolves the injected-interface default conflict.
The naming/component-reset assertions provide runtime regression coverage.
The mixin remains declared in `kubegroovy.mixins.json`, and the interface
injection declaration in `interfaces.json` is unchanged.

## Final verification

`test runGametest build` completed successfully. JUnit reported 94 tests,
zero failures/errors, and nine skips. The dedicated server reported all 70
required holders passed (the count in AGENTS.md is older than the current
suite), with 7/7 startup and 17/17 server scripts loaded with zero errors.

The final fresh client run reported 81/81 holders passed, zero failures, and
zero pending. Startup (8/8), client (3/3), and server (17/17) scripts loaded
with zero errors. Existing intentional missing-texture/invalid-language
diagnostics still appear in the client log. The existing icon-export holder
failed its batch-size assertion in an earlier full run, then passed both
alone and in the final full run; that intermittent failure was not changed.

After stopping the client gracefully, `coverageClientSummaryJson -x test`
completed successfully:

| Metric | Baseline | Final |
| --- | ---: | ---: |
| Instructions | 47.09% | 47.40% |
| Branches | 35.23% | 35.47% |
| Lines | 48.40% | 48.78% |
| `core` instructions | 11.52% | 16.32% |

The final report has 398 more covered instructions and 99 more covered lines
overall. `core` has 349 fewer missed instructions; it remains the largest
gap. `KubeGroovyConditionalCallbackProperty` now has all 41 instructions
covered. Overall deltas can also include differences in engine paths taken
across client runs.

The ambiguous `persistentData` property lookup remains a separate issue;
this audit does not change its compatibility-sensitive resolution behavior.
The explicit Forge-data fixture passes on both dedicated and client runs.
