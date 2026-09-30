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

## Type suggestions

The type-suggestions script reads suggestions.json next to its plugin.groovy.
KubeGroovy writes that catalog to local/kubegroovy/live-plugin/type-suggestions/
and installs it into each discovered LivePlugin directory when
/kgy generate-typings runs. Providers execute on the server thread against the
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
