# KubeGroovy LivePlugin prototype

This folder contains the Kotlin LivePlugin port of the registry editor support from `plugin/intellij`.

The single `plugin.kts` script registers IntelliJ Groovy extensions for:

- resolving `$StartupEvents` and synthetic `registry` overloads;
- completing registry keys from the running game's registry access and opening completion while typing;
- inferring `RegistryKubeEvent<T>` for the handler closure parameter from a string key or a `ResourceKey<Registry<T>>` expression, using the runtime `RegistryType` data and fully qualified generic type names;
- showing `$StartupEvents` and the runtime script bindings with the IDE's class name color and completion;
- exposing script-facing aliases, injected interface methods, context-free signatures, wrapper overloads, and wrapped constructors for runtime bindings, registered event types, and event target types;
- hiding raw context-prefixed and renamed method entries when the script-facing form is available, and limiting event-group completion to registered handlers.

## Try it with LivePlugin

Run `/kgy generate-typings` in the game. It copies the bundled script to `live-plugins/kubegroovy/plugin.kts` in the newest detected IntelliJ IDEA config directory that has LivePlugin installed. If none is found, it uses the newest detected IDEA config. On Windows, this is typically `%APPDATA%/JetBrains/IntelliJIdea<version>/live-plugins/kubegroovy/plugin.kts`. If you use a nonstandard location, set the JVM property `kubegroovy.livePluginDirectory` to the LivePlugin directory before generating typings.

Install the LivePlugin IDE plugin once, then run or reload `kubegroovy` from the Live Plugins tool window.

The generator only replaces the script while it still carries its generated marker, so it leaves manual edits intact and reports a conflict rather than overwriting them.

The generated member data is limited to runtime binding, registered event, and event target receiver types. IntelliJ resolves ordinary members for those types from the generated project classpath.

`plugin/intellij` remains available as the packaged-plugin reference implementation while this runtime-loaded version is evaluated.
