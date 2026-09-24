# KubeGroovy

KubeGroovy is a fork of [KubeJS](https://github.com/KubeJS-Mods/KubeJS) that runs **Groovy** scripts (Groovy 6.x) instead of JavaScript.

**Note: If you are a script developer (i.e. pack dev, server admin, etc.), check the `kubegroovy/` folder in your game directory and the example `main.groovy` files to get started.**

## Introduction

KubeGroovy is a Minecraft mod which lets you create scripts in the Groovy programming language to manage your server using events, change recipes, add loot tables, customise your world generation, add new blocks and items, or use custom integration with other mods for even more advanced features!

Scripts live in the `kubegroovy/` game directory:

- `startup_scripts/` - loaded once during game startup (`.groovy` files, support `// priority: N`, `// requires: <modid>` headers)
- `server_scripts/` - reloaded with `/reload`
- `client_scripts/` - reloaded with `F3+T`

Events are posted to Groovy closures, e.g.:

```groovy
ServerEvents.recipes { event ->
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

ItemEvents.modification { event ->
    event.modify('minecraft:ender_pearl' as RegistryPredicate) { item ->
        item.maxStackSize = 64
    }
}
```

The equivalent `value.asType(Target)` form is supported. Ordinary Java/Groovy
method resolution remains unchanged, and unregistered targets keep normal
Groovy cast behavior.

Scripts run on a sandboxed `GroovyClassLoader` (`ClassCodeExpressionTransformer` + `CompilationCustomizer` + class filter): `System.exit`, `Runtime.exec`, reflection abuse (`setAccessible`), and classloader tricks are blocked.

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

KubeGroovy offers native Java/Groovy type access in script files. While builtin filters exist to prevent users from accessing any internal or potentially harmful packages added by Minecraft or its libraries directly, you may still want to explicitly deny access to certain classes or packages in your mod (or explicitly allow certain classes *within* a generally blacklisted package). To do this, you may either provide a class filter using a KubeGroovy plugin *or* you can avoid adding KubeGroovy as a dependency entirely by providing a simple `kubegroovy.classfilter.txt` file in your mod's `resources` with the following format (Note that comments aren't allowed in the actual file):

```diff
+mymod.api // This will *explicitly allow* anything from the mymod.api package to be used in KubeGroovy
-mymod.api.MyModAPIImpl // This will deny access to the MyModAPIImpl class, while keeping the rest of the package accessible
-mymod.internal.HttpUtil // This will *explicitly deny* your class from being used in KubeGroovy
```

### Recipe Schemas

In addition to registering recipe schemas through your plugin, they can be made via a json file in the `kubegroovy/recipe_schema` registry. Json schemas are the preferred method of declaring schemas and can be [datagenned](https://docs.neoforged.net/docs/1.21.1/resources/#data-generation) with the provided `RecipeSchemaProvider`!

**Important!** In order to use this data provider, you *must* add kubegroovy as an existing mod with the `'--existing-mod', 'kubegroovy'` arguments. See [NeoForge's docs](https://docs.neoforged.net/docs/1.21.1/resources/#command-line-arguments) for how to do that.

## Contributing to KubeGroovy

### Getting Started

If you want to contribute to KubeGroovy, clone the repository using Git and import the gradle project using an IDE of your choice!

### Building

Building KubeGroovy from source should be rather straightforward, just run `gradlew build` in the root project, and the jar files should be in `build/libs` directory.
