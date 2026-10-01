# LivePlugin scripts

All six scripts apply only to Groovy PSI files with a `.groovy` filename under:

- `kubegroovy/startup_scripts/**`
- `kubegroovy/client_scripts/**`
- `kubegroovy/server_scripts/**`

The `kubegroovy` directory can appear anywhere in the project path, including
`src/test/kubegroovy`. Directory names must match complete path segments.
Completion copies use their original file. Java files, Gradle scripts, unrelated
Groovy files, and contexts without an original file path are excluded.

Method aliases and Object overloads use Groovy's `NonCodeMembersContributor` and
check the **call site** before contributing methods. They must not use a global
`PsiAugmentProvider`: it changes the target Java class for every consumer and has
no call-site context. Documentation likewise checks the requesting location,
while reading annotations from the referenced Java declaration.

After updating the scripts, run each folder's `install.bat` and reload the six
scripts in LivePlugin. Restart IDEA once after replacing the old global augment
providers to clear any existing resolution caches.

Generated catalogs are read directly from the current IntelliJ project's
`local/kubegroovy/live-plugin/<script>/` directory, using the same project root as
exported icons. `/kgy generate-typings` writes the five JSON files there and
installs only scripts and logo assets into the discovered JetBrains LivePlugin
folders. The manual installers also skip JSON files. Existing JSON copies in
JetBrains folders are ignored.

Each project has its own cached catalogs. Updates are observed on the next
completion or resolution request without reloading the plugin. Missing or
malformed updates retain only that project's last valid catalog; a project
without a generated catalog starts empty.

Method aliases copy the declared `from` overloads when the editor can see them.
Generated `aliases.json` entries also include `signatures`, an array of Java method
declarations with the original name, parameters, return type, type parameters,
modifiers, and throws types. When `from` is missing (for example, Minecraft classes
without runtime interface injection), those declarations supply the alias methods.
An existing `to` with the same erased parameter signature still wins. Older entries
without `signatures` continue to require `from`. Regenerate typings and reload the
updated method-aliases script to enable this in a modpack project.

The standalone IDE fixture project loads these actual scripts without executing
their LivePlugin registration bodies. It uses the IDE's Groovy runtime and an
SDK from the fixture JVM; versions are pinned in the root `gradle.properties`.

```powershell
.\gradlew.bat -p plugin/live-plugin/tests test --no-configuration-cache
```

Set `INTELLIJ_HOME` to use an installed IDEA instead of downloading the pinned
version. The fixture project has its own build and test host, separate from the
mod build. It covers script scoping, catalog reloads, container/generic types,
icon caches, and actual completion selection and document insertion.

The bindings script exposes `classValue: true` entries as implicit aliased class
imports for the matching script side, mirroring the engine autoimports. Aliases
such as `$Item` resolve to their classes for static member completion and type
references. Selecting a binding suggestion inserts only its name. Instance and
event-group bindings remain implicit variables. Reload bindings after updating
the script; no explicit alias imports are needed.

## Registry event and builder types

The events script constructs method type variables directly from their PSI
declarations. This lets IDEA infer `RegistryKubeEvent<Block>` from
`Registries.BLOCK`, `'block' as ResourceKey<Registry<Block>>`, or plain `'block'`, including implicit
event bindings and the closure's `it` parameter. A generated facade JAR also
works in the IDE fixtures, but is unnecessary for this inference.

Registry methods include an Object-target coercion overload supplied by the
events provider itself. The object-overload provider reads declared Java methods
and cannot rewrite another contributor's synthetic event methods. For a known
registry string literal, the events provider specializes the callback using
`_registryTargets`; names without a namespace use `minecraft:`. Unknown or dynamic
targets remain applicable with `RegistryKubeEvent<?>`, preserving the generic
ResourceKey overload for typed keys. Invalid callbacks still produce normal
applicability diagnostics.

Registry-name completion uses the synthetic event callback signature to retain
the `Registry` value catalog even when the applicable target parameter is Object.
It also works while the callback is still missing or the target argument is empty.

The type-suggestions catalog also includes `registryBuilderTypes`, keyed by the
registry element type. In the second argument of registry `create(id, type)`, it
offers that registry's named builders, including short `kubegroovy:` names such
as `detector` and full namespaced IDs. Default-only registrations are excluded.
The existing insert handler quotes the chosen name and preserves explicit casts.
The first argument retains normal identifier completion. Regenerate typings and
reload type-suggestions to enable these entries in an existing project.

`/kgy generate-typings` now includes `_registryTargets` and `_builderTypes` in
`events/events.json`, using the runtime builder registrations, including addons.
The events script specializes the return type of registry `create` calls from
the event's element type and a literal builder name. For block events,
`create('id', 'detector')` resolves to `DetectorBlock.Builder`, while `create('id')`
resolves to the registered default `BlockBuilder`. Completion and chained calls
use those concrete classes without changing the runtime method signatures.

Exact namespaced builder IDs take precedence; otherwise an unambiguous path
alias is used, as in the runtime's named lookup. Unknown or dynamic names,
missing builder classes, conflicting aliases, and multiple registries sharing
the same element type retain the declared return type. Older catalogs without
builder metadata retain the declared builder types too. Run the updated mod's
`/kgy generate-typings`, then reload the events script in LivePlugin.

## Type suggestions

The type-suggestions script reads
`local/kubegroovy/live-plugin/type-suggestions/suggestions.json` directly from
the current project. Providers execute on the server thread against the
loaded server KubeGroovyContext; the snapshot is shared by startup, server, and
client script completions.

The implementation stays in one script for generated installation. Its
`TypeSuggestionSupport` helpers handle scope, type-name syntax and diagnostics;
`TypeSuggestionCatalog` publishes immutable snapshots; `TypeSuggestionPsi`
infers types against one snapshot; and `TypeSuggestionIcons` owns icon loading
and caches. `TypeSuggestionInsertion` and its insert handler select a document
range and replace it with a quoted ID. The completion provider coordinates these
helpers. Keep reflection for differing map-entry PSI shapes in the PSI adapter.

The insert handler commits the document before querying PSI and after editing
it. A tracked range preserves the selected argument or container part while
IntelliJ inserts lookup text, including any surrounding quotes and explicit
casts. IDs are escaped as Groovy string literals, including dollar signs and
control characters.
Type-name positions, including the type after `as`, do not offer identifier
suggestions. Strings, empty argument slots, and container entries still do.
Catalog snapshots retain the last valid configuration on a malformed update;
their generation isolates type-dependent icon caches during concurrent reloads.

Addon plugins can register values and wrapper behavior through
KubeGroovyPlugin.registerTypeSuggestions:

~~~~java
@Override
public void registerTypeSuggestions(TypeSuggestionRegistry reg) {
    reg.register(Item.class, (cx, typeInfo) -> cx.lookupRegistry(typeInfo, null)
        .keySet().stream().map(key -> key.identifier().toString()));
    reg.registerModifierFor(Item.class, TagKey.class, (cx, typeInfo) ->
        cx.lookupRegistry(typeInfo, null).listTagIds()
            .map(tag -> tag.location().toString()));
    reg.registerDelegate(ItemStack.class, Item.class);
    reg.registerDelegate(Ingredient.class, Item.class);
    reg.registerDelegate(Ingredient.class,
        TypeInfo.of(TagKey.class).withParams(TypeInfo.of(Item.class)), "#");
    reg.registerDelegate(Optional.class);
    reg.registerDelegate(Holder.class);
}
~~~~

Registrations combine in plugin order and retain duplicate strings and encounter
order. Provider streams are closed after the snapshot is consumed. A provider
failure stops generation and leaves the previous suggestions file in place.

The generated JSON has schemaVersion, a values object keyed by complete
TypeInfo.toStringFQN() names, ordered fixed delegates (source, target, and
prefix), and genericDelegates mapping a one-parameter wrapper FQN to its
argument index. The IDE resolves these rules recursively, including nested
Optional<T> and collection/map literal positions. Plain TagKey<T> values have
no prefix; a delegate such as Ingredient → TagKey<Item> adds #.

Completion icons come from the current IntelliJ project's
`local/kubegroovy/export/icons-32/` directory (the legacy spelling
`icon-32/` is also recognized). Item and fluid icons use the exporter's
`item/<namespace>/<path>.png` and `fluid/<namespace>/<path>.png` layout.
TagKey suggestions use `tags/item/`, `tags/block/`, or `tags/fluid/` according
to the tag's generic element type; a leading `#` is removed for the lookup.
On plugin load, existing project exports are decoded in the background from
the `item/`, `fluid/`, optional `block/`, and `tags/{item,block,fluid}/`
folders. A project's first completion also starts this preload if the plugin
was loaded before that project opened.
When no exported icon is available, completion uses the vector
`bindings/logo.svg` from the sibling LivePlugin folder, with the PNG logo as a
fallback. Resolved icons are cached per project, identifier, and asset path, so
delegated types that resolve to the same asset share an entry. The cache checks
candidate file attributes once per completion, sharing checks for common fallback
files. Prefix matching happens before icon resolution, and candidate discovery
stops at the first usable icon so preloaded exports avoid fallback path work.
Newly exported, updated, or removed icons are observed on the next completion
without reloading the plugin. Timing logs separate type inference and icon work
and report both considered and matching suggestions. Run `/kgy export-icons` in
a client world to refresh the project exports.

## Icon export throughput

The exporter draws up to 64 items and fluids into separate 16-unit GUI cells,
flushes the GUI renderer once, and takes one screenshot for the batch. Each cell
is clipped to prevent oversized models from bleeding into neighboring icons.
The grid uses up to eight columns and is recomputed each frame to fit the
framebuffer and requested output size, including after a window resize. Output
paths, sizes, fluid tints, and chroma-keyed transparency stay the same.

PNG cropping and encoding run on Minecraft's IO pool. At most two batches can be
waiting for readback or saving, which bounds memory use and applies backpressure
when disk writes fall behind. Tag overlays start only after every base PNG write
has completed; they process up to 64 tags per frame within a four-millisecond
budget. A failed icon write does not abort its neighbors. Closing the export
screen cancels the remaining work, and late callbacks cannot change its result.

Completion logs report elapsed time and the number of screenshot batches.
Batching reduces the number of full-framebuffer captures by up to 64 times;
rendering and PNG encoding still cost time, so this is not an overall speedup
guarantee. JVM tests cover grid bounds and resized framebuffers. The client
`icon_export` holder checks representative sizes, mixed entries across rows and
batches, exact item pixels, fluid tints, tag ordering, and an unwritable PNG.
