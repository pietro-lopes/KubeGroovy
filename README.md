# KubeGroovy

[KubeGroovy](https://mods.uncandango.dev/kubegroovy/) is a fork of [KubeJS](https://github.com/KubeJS-Mods/KubeJS) that runs **Groovy** scripts (Groovy 6.x) instead of JavaScript.

**Note: If you are a script developer (i.e. pack dev, server admin, etc.), check the `kubegroovy/` folder in your game directory and the example `main.groovy` files to get started.**

## Introduction

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


## Issues and Feedback

If you think you've found a bug with the mod or want to ask for a new feature, feel free to open an issue here on GitHub!

## License

KubeGroovy is distributed under the GNU Lesser General Public License v3.0, or LGPLv3. See our [LICENSE](LICENSE.txt) file for more information. Upstream KubeJS is also LGPLv3, see the original repository for details.

## Creating addons (WIP)

## Contributing to KubeGroovy

### Getting Started

If you want to contribute to KubeGroovy, clone the repository using Git and import the gradle project using an IDE of your choice!

### Building

Building KubeGroovy from source should be rather straightforward, just run `gradlew build` in the root project, and the jar files should be in `build/libs` directory.
