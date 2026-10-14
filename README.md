# KubeGroovy

KubeGroovy is a fork of [KubeJS](https://github.com/KubeJS-Mods/KubeJS) that runs **Groovy** scripts (Groovy 6.x) instead of JavaScript.

**Note: If you are a script developer (i.e. pack dev, server admin, etc.), check the `kubegroovy/` folder in your game directory and the example `main.groovy` files to get started.**

## Introduction

The shared [script security policy](docs/script-security.md) describes allowed
file access and migration from per-side filters and classfilter text files.

KubeGroovy is a Minecraft mod which lets you create scripts in the Groovy programming language to manage your server using events, change recipes, add loot tables, customise your world generation, add new blocks and items, or use custom integration with other mods for even more advanced features!

Scripts live in the `kubegroovy/` game directory:

- `startup_scripts/` - loaded once during game startup (`.groovy` files, support `// priority: N`, `// requires: <modid>` headers)
- `server_scripts/` - reloaded with `/reload`
- `client_scripts/` - reloaded with `F3+T`

Events are posted to Groovy closures, e.g.:

KubeGroovy script bindings use a `$` prefix, so imported classes keep their
unprefixed names.

```groovy
$ServerEvents.recipes { event ->
    event.shaped('minecraft:diamond', ['AAA', 'AAA', 'AAA']) {
        key('A', 'minecraft:dirt')
    }
}
```

Groovy collection syntax, closures, maps, GStrings, and normal imports are
available to scripts. Values that have a registered KubeGroovy type wrapper use
an explicit cast, so the conversion is visible at the call site:

```groovy
import dev.uncandango.kubegroovy.util.registrypredicate.RegistryPredicate

$ItemEvents.modification { event ->
    event.modify('minecraft:ender_pearl' as RegistryPredicate) { item ->
        item.maxStackSize = 64
    }
}
```

The equivalent `value.asType(Target)` form is supported. Ordinary Java/Groovy
method resolution remains unchanged, and unregistered targets keep normal
Groovy cast behavior.

Scripts run on a sandboxed `GroovyClassLoader` (`ClassCodeExpressionTransformer` + `CompilationCustomizer` + class filter): `System.exit`, `Runtime.exec`, reflection abuse (`setAccessible`), and classloader tricks are blocked.

## Exporting icons

From a client world, run `/kgy export-icons [scale] [mod]` to export item and
fluid icons together, followed by icons for loaded item, fluid, and block
tags. The scale defaults to 32 pixels and accepts values from 1 to 128; the
optional `mod` argument filters item/fluid entries and tag ids by namespace.
Files are written under `local/kubegroovy/export/icons-<scale>/`, with items
at `item/<namespace>/<registry-path>.png`, fluids at
`fluid/<namespace>/<registry-path>.png`, and tags at
`tags/<item|fluid|block>/<namespace>/<tag-path>.png`.

Tag icons use the first member whose item or fluid icon exists in that output
directory. Nested tags are resolved to their ordered members, and later
members are tried when earlier icons are absent. Block members use their item
variant. The 9×9 `tag_badge.png` texture overlays each icon in the top-right
corner; members from another namespace can use icons already exported at the
same scale.

## Issues and Feedback

If you think you've found a bug with the mod or want to ask for a new feature, feel free to open an issue here on GitHub!

## License

KubeGroovy is distributed under the GNU Lesser General Public License v3.0, or LGPLv3. See our [LICENSE](LICENSE.txt) file for more information. Upstream KubeJS is also LGPLv3, see the original repository for details.

## Creating addons

To add a Gradle dependency on KubeGroovy, add the following to your `build.gradle`:

```groovy
repositories {
    mavenCentral() // Apache Groovy: org.apache.groovy:groovy
}

dependencies {
    api("dev.uncandango:kubegroovy-neoforge:$kubegroovy_version")
}
```

### Creating a plugin

KubeGroovy plugins are the main way to add integration to your mods through code. They contain various convenient hooks for addon developers, to allow things such as:

- performing certain actions during or after plugin or KubeGroovy initialisation (`init`, `afterInit`, etc.)
- adding classes to the class filter (`registerClasses`) as well as custom bindings (`registerBindings`) and type wrappers (`registerTypeWrappers`) for easier interaction with native code in user scripts
- registering custom recipe handlers for modded recipe types
- registering custom event handler groups for the KubeGroovy event system (`registerEvents`, this is **necessary** in order to have the event group be accessible from scripts)
- attaching extra data to players, worlds or the server, such that it can be accessed by script developers later (`attach(Player|Level|Server)Data`)

You must add your plugin class in a `src/main/resources/kubegroovy.plugins.txt` file, optionally with mod id at end, e.g.:

```
dev.uncandango.kubegroovy.mekanism.MekanismKubeGroovyPlugin mekanism
```

### Adding bindings

Similarly to adding custom recipe types, you may also add **custom bindings** to KubeGroovy. Bindings can be anything from single value constants to Java class and method wrappers, and can be constrained to individual scopes, contexts and script types!

### Setting class filters

Register rules through `KubeGroovyPlugin.registerClasses`. One frozen policy applies to startup, server, and client scripts; the hook runs once after plugin discovery. Bindings do not automatically allow their classes.

```java
@Override
public void registerClasses(ClassFilter filter) {
	filter.denyPackage("mymod.internal");
	filter.allow(MyModAPI.class);
	filter.denyMethods(MyModAPI.class, "unsafeOperation");
	filter.denyMethod(MyModAPI.class, "overloaded", String.class);
}
```

The sided constructor, `ScriptType.classFilter`, and `kubegroovy.classfilter.txt` configuration have been removed. See [script security and addon migration](docs/script-security.md) for precedence, additional member rules, path confinement, and the trusted-addon boundary.

### Recipe Schemas

To reuse a component with a mod's runtime codec, use `with_codec`:

```json
{
	"type": "with_codec",
	"component": "item_stack_template",
	"codec": {
		"class": "com.example.mod.RecipeSerializer",
		"field": "RESULT_CODEC"
	}
}
```

The codec's `field` is optional and defaults to `CODEC`; a class-path string such as `"codec": "com.example.mod.Result"` is shorthand for that default. The class must declare a static `Codec` or `MapCodec` field. Private fields are supported, and a `MapCodec` is converted to its value codec. The parent supplies script conversion, matching, replacement, and validation. Without `conversion`, the reflected codec must produce the parent's value type; erased Java generics cannot verify this during schema loading. Missing classes, fields, and invalid field types report schema decode errors. Reflection handles are not exposed to scripts.

For a codec returning a mod-specific value class, add a bidirectional `conversion`:

```json
{
	"type": "with_codec",
	"component": "sized_ingredient",
	"codec": {"class": "com.example.mod.IngredientStack", "field": "ITEM_CODEC"},
	"conversion": {
		"class": "com.example.mod.IngredientStack$Item",
		"properties": {"count": "amount"},
		"parent_constructor": ["ingredient", "count"]
	}
}
```

Properties map parent names to foreign names; omitted entries keep the same name. Readers use an exact-name no-argument method, then a field. Use `{"field": "amount"}` or `{"method": "amount"}` to choose explicitly. Constructors are inferred from record components or retained parameter names when there is one compatible candidate. Otherwise provide `parent_constructor` and/or `codec_constructor` as ordered arrays of parent property names. Incompatible properties and ambiguous constructors report decode errors. Conversion invokes the selected constructors and readers in both directions; the foreign codec retains its JSON defaults and validation. For `sized_fluid_ingredient`, both classes using `ingredient` and `amount` need no property aliases.

Use `fluid_stack_template` for serializer fields backed by NeoForge's `FluidStackTemplate.CODEC`. Values are `FluidStackTemplate` instances; call `.create()` to obtain a `FluidStack`. JSON accepts a fluid ID (defaulting to one bucket) or an object with `id`, required positive `amount`, and optional `components`. Script inputs also accept fluid strings and non-empty `FluidStack` values.

Vanilla crafting, cooking, stonecutting, and smithing-transform results use the `item_stack_template` component. It accepts vanilla item IDs or objects with `id`, `count`, and `components`, and exposes an `ItemStackTemplate` through `recipe.get('result')`. Call `.create()` to obtain an `ItemStack`. Recipe builders also accept item strings and `ItemStack` values, converting them to templates.

In addition to registering recipe schemas through your plugin, they can be made via a json file in the `kubegroovy/recipe_schema` registry. Json schemas are the preferred method of declaring schemas and can be [datagenned](https://docs.neoforged.net/docs/1.21.1/resources/#data-generation) with the provided `RecipeSchemaProvider`!

**Important!** In order to use this data provider, you *must* add kubegroovy as an existing mod with the `'--existing-mod', 'kubegroovy'` arguments. See [NeoForge's docs](https://docs.neoforged.net/docs/1.21.1/resources/#command-line-arguments) for how to do that.

## Contributing to KubeGroovy

### Getting Started

If you want to contribute to KubeGroovy, clone the repository using Git and import the gradle project using an IDE of your choice!

### Building

Building KubeGroovy from source should be rather straightforward, just run `gradlew build` in the root project, and the jar files should be in `build/libs` directory.
