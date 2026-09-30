# Groovy fixture conventions

Pass script values directly to typed API parameters so the fixtures exercise
implicit conversion. Import classes instead of spelling out package names in
script bodies. `VanillaItemPredicate` aliases Minecraft's `ItemPredicate` to
avoid colliding with KubeGroovy's predicate class in `wrapper_checks.groovy`.

## Builder configuration

Configure builders with `.with {}`. Keep callback parameters explicit and name
them with a `Builder` suffix so names such as `sound` cannot shadow delegated
methods. If a method returns a child builder, configure that returned object in
its own `.with {}` block. Keep objects in separate locals when later assertions
need them: `.with` returns the closure's last result, not necessarily the builder.

Creative-tab icon and content suppliers are declared outside `.with` and passed
to the setters inside it. Moving these deferred suppliers into the delegated
closure changes their conversion behavior; the registry game tests cover both
the carrot icon and rebuilding the tab contents.

## Explicit casts retained

| Fixture | Casts | Reason |
| --- | --- | --- |
| `server_scripts/wrapper_checks.groovy` | Wrapper conversion and rejection cases, including chained and generic casts | The cast itself is the operation under test. Assertions often accept `Object`, which supplies no conversion target. Intermediate casts select the source type being tested. |
| `server_scripts/syntax_checks.groovy` | `as Set`, `as String` | Exercise Groovy collection coercion and ordinary cast syntax. |
| `server_scripts/binding_checks.groovy` | `as ItemStack` in `stackOf` | Construct a stack before calling `setCount` or inspecting its properties. |
| `server_scripts/binding_checks.groovy` | `as MutableComponent`, `as Ingredient` | Inspect the resulting component/ingredient and verify `isIngredient` distinguishes a wrapped object from a string. |
| `server_scripts/binding_checks.groovy` | `as RegistryPredicate<Item>` | Exercise generic registry conversion inside the server's asynchronous context. |
| `server_scripts/binding_checks.groovy` | `as Runnable` | Adapt a map containing a `run` closure to the Java interface. |
| `server_scripts/recipe_events.groovy` | `as ItemStack` in `stackOf` | Set the recipe output count before passing the stack to the recipe API. |
| `server_scripts/recipe_events.groovy` | `as Ingredient` in `replaceInput` | The replacement parameter is `Object`; it does not provide an ingredient target type. |
| `server_scripts/coverage_wrappers.groovy` | `as ItemStack` for `original` | Inspect the original stack to verify counted copies do not mutate it. |
| `startup_scripts/builder_method_misc.groovy` | `as Holder<ParticleType<?>>` | Resolve the registry holder before calling `value`, `codec`, and `streamCodec`. |
| `client_scripts/client_coverage_wrappers.groovy` | Closure `as BlockTintFunction` | Construct the interface value whose invocation and tint behavior are exercised directly. |
| `client_scripts/client_coverage_wrappers.groovy`, `client_scripts/client_coverage_assets.groovy` | Map keys/values `as KubeColor` | Supply color objects within a map; Java texture remapping consumes the entries as `KubeColor` objects. |

## Placeholder textures

Fixture textures under `assets/kubegroovy/textures` reuse the mod's
`example_block.png` as a visible placeholder. They provide the texture IDs used
by the generated fixture models and particle definitions. Custom model callbacks
must supply their texture maps, and block overlays must use a texture included
in the block atlas.

Do not add textures for `coverage_missing_xyz` or `coverage_missing_abc`.
`client_coverage_wrappers.groovy` and `client_coverage_assets.groovy` deliberately
use these IDs to check empty loads and failed stencil/mask operations. The asset
fixture also deliberately saves an empty texture as `coverage_client/missing`.
The failed load/save diagnostics from those probes are expected.
