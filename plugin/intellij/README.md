# KubeGroovy IntelliJ prototype

This plugin prototype replaces the generated `StartupEvents` GDSL entry for the registry event with IntelliJ Groovy PSI contributions.

It contributes the `$StartupEvents` binding and synthetic registry overloads for `String` targets and `ResourceKey<Registry<T>>` targets. The completion provider offers `item`, `block`, and `fluid` while typing in the first argument; selecting one inserts a plain string such as `"block"`, which the runtime target transformer already accepts. A Groovy variable type enhancer reads either the string or a typed key and gives the first closure parameter its matching `RegistryKubeEvent<T>` type. The binding reference uses the IDE's class name color so it reads like a type reference.

Build the plugin from the repository root:

```powershell
.\gradlew.bat :plugin:intellij:buildPlugin
```

Launch an IntelliJ sandbox with the plugin:

```powershell
.\gradlew.bat :plugin:intellij:runIde
```

The prototype currently recognizes `$StartupEvents.registry(...)` and only includes the three common registry types. Its PSI members are editor-only and do not alter KubeGroovy's runtime API.
