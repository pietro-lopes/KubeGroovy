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

IDE fixture regressions load these actual scripts without executing their
LivePlugin registration bodies:

```powershell
.\gradlew.bat -I plugin/live-plugin/tests/fixture-tests.init.gradle :plugin:intellij:test --tests '*LivePluginScopeTest' --no-configuration-cache
```

## Type suggestions

The type-suggestions script reads suggestions.json next to its plugin.groovy.
KubeGroovy writes that catalog to local/kubegroovy/live-plugin/type-suggestions/
and installs it into each discovered LivePlugin directory when
/kgy generate-typings runs. Providers execute on the server thread against the
loaded server KubeGroovyContext; the snapshot is shared by startup, server, and
client script completions.

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
candidate file timestamps so newly exported or updated icons can replace the
fallback without reloading the plugin. Run `/kgy export-icons` in a client world
to refresh the project exports.
