# Script access policy and addon migration

`ScriptManager.getClassFilter()` returns one frozen policy shared by startup,
server, and client scripts. Plugin discovery happens first; `registerClasses`
then runs once per loaded plugin. Script reloads reuse the same policy.

`kubegroovy.classfilter.txt`, `ClassFilter(ScriptType)`,
`ClassFilter.scriptType`, `ScriptType.classFilter`, and
`createClassFilter(ScriptType)` have been removed. Move registrations into your
plugin. Bindings no longer automatically allow their classes.

```java
@Override
public void registerClasses(ClassFilter filter) {
	filter.denyPackage("example.addon.internal");
	filter.allow(ScriptHelpers.class);
	filter.denyMethods(ScriptHelpers.class, "unsafeOperation");
	filter.denyMethod(ScriptHelpers.class, "overloaded", String.class);
	filter.denyMethodName("dangerousAcrossClasses");
	filter.denyReturnType(MyCapability.class);
	filter.denyParameterType(MyCapability.class);
	filter.denyConstructor(MyType.class, String.class);
	filter.denyField(MyType.class, "internalState");
}
```

Ordinary deny registrations have equivalent allow registrations. `allow` and `deny`
match exact classes; `allowPackage` and `denyPackage` match package boundaries.
The most specific package rule wins, with deny winning ties. Exact class allows
can override package denies; exact class denies cannot be overridden. Member
denies and `@HideFromGY` win over member allows. Owner rules include inherited
methods and concrete receiver subclasses. Return/parameter rules use assignable
types. Registration closes before compilation; later mutations throw.

A new `ClassFilter` has no rules. `KubeGroovyPlugins.createClassFilter` first
installs the sandbox baseline through
`BuiltinKubeGroovyPlugin.registerSandboxClasses`, then runs each plugin's
`registerClasses`. The baseline uses ordinary registrations: sandbox packages
and desktop packages are `deny` + `denyPackage`, dangerous JVM classes are exact
`deny`s, and `java.io` is a package deny with exact allows for `File`,
`Serializable`, `Closeable`, and `IOException`. Installed plugins are trusted
not to reopen these classes or packages; an exact class allow would override a
sandbox package deny.

Rules that cannot be expressed by class name use predicate registrations:
`denyTypes(Predicate<Class<?>>)` (runtime types, including arrays, receivers,
return values, and parameters; class allows do not override it),
`denyMethodNames(Predicate<String>)` (all receivers, including compile-time
and early dynamic-dispatch checks), and
`denyMemberNames(BiPredicate<Class<?>, String>)` (receiver-specific names;
member allows do not override it). Parameter types are checked against type
rules only, not class-name or package denies. Predicates must be stable and safe
for concurrent lookups after freeze. Registration invalidates lookup caches and
is rejected after freeze.

The previous `denyCapability*` methods, `isCapabilityClassDenied`,
`isCapabilityTypeDenied`, and the static `ClassFilter.mandatoryClass` /
`mandatoryType` queries have been removed. Use `deny`/`denyPackage` for names
and the predicate registrations above for the rest; `isTypeDenied` queries type
rules.

Access otherwise defaults to allowed. Class allowances do not grant permission
to blocked members or denied types. The sandbox baseline blocks raw
I/O, network APIs, process/JVM control, reflection, method handles, unsafe/native
entrypoints, arbitrary executors, dynamic evaluation, and metaclass mutation.
Minecraft's existing event loops and approved KubeGroovy helpers remain trusted.

Scripts cannot access `javax.swing`, `java.awt`, `java.applet`, `javax.sound`, or
`javax.imageio`, including their subpackages and runtime subtypes. These APIs can
perform file or network I/O internally from string addresses (for example,
`JEditorPane(String)` and `setPage(String)`), bypassing `File`/`Path` validation.
The subtype part is a type rule, so it applies even when a plugin allows the
subclass or member. Use approved helpers for file access and Minecraft APIs for UI.

`groovy.util.FileTreeBuilder` and `dev.latvian.apps.tinyserver` (including its
subpackages) are also sandbox denials. The builder performs filesystem writes
and copies internally, and TinyServer exposes socket binding and filesystem
serving.

`com.mojang.realmsclient` and its subpackages are sandbox denials as well;
scripts cannot access the Realms client APIs.

`java.util.Formatter` and `groovy.ui` (including subpackages) are sandbox
denials. Formatter's filename constructors open files internally; Groovy UI
entrypoints expose file/URL reads and script execution outside the sandbox.
Use `String.format` for formatting strings.

`scala.sys` and its subpackages are sandbox denials, blocking these system and
process APIs.

`ai.djl`, `org.jsoup`, `de.keksuccino`, `org.apache.logging.log4j.core`,
`com.electronwill.nightconfig`, `crash_assistant_relocated_libs`,
`dev.kostromdan.mods.crash_assistant`, `scala.io`, `dev.langchain4j`,
`opennlp.tools`, `me.lucko.spark`, `com.typesafe`, `com.moandjiezana`,
`ai.onnxruntime`, and `com.euphoriapatches.shadow.com.google.gson`, including
all subpackages, are sandbox denials.

Package-private static Java methods are blocked during metaclass indexing and
guarded invocation, even when a plugin allows the member. Groovy's internal
reflection cannot make these helpers available to scripts. Methods declared by
classes compiled through the sandbox script loader retain their normal access;
this rule does not change protected `super` calls or instance-method visibility.

The exposed BuilderBase lifecycle declarations allow script subclasses to call
methods such as `super.generateLang(lang)`. Unrelated helpers such as FoodBuilder,
ItemBehaviorFunctions, and TextActionBuilder retain their hidden internal members.
The two-argument data-component builder `set` methods remain hidden in favor of
`kgy$set`. The global class and member policy,
type and name rules, and runtime value/path guards still apply to these calls.

Compilation checks imports and authored expressions, including constructors,
casts, method pointers, fields, and properties. Ordinary annotations (including
NeoForge `@SubscribeEvent`) and benign Groovy transforms are allowed subject to
the class policy. The annotation deny list blocks `ASTTest`, `CompileStatic`,
`TypeChecked` (compiler extension scripts), dependency grabbing, `AnnotationCollector`
(transform redirects), authored `BaseScript`, and transform-definition annotations.
Known dangerous processors remain blocked through annotation aliases. Ordinary
annotation arguments may contain enum values and class literals. Engine metadata is
handled separately. Runtime guards cover ordinary and invokedynamic invocation,
dynamic names, aliases, coercions, constructors, properties, and fields.

`File` and `Path` are confined values, with a small set of path operations.
Raw file methods and Groovy file extensions are blocked. Use `JsonIO`, `NBTIO`,
and other approved helpers for I/O. Relative paths resolve against `GAMEDIR`.
Validation rejects traversal and existing symlink/junction escapes, including
nonexistent descendants of linked directories. The `mods` directory and all its
descendants are denied, including link aliases, to prevent scripts from planting
mods that execute on the next launch. I/O helpers validate at their
filesystem boundaries; filesystem races caused by external processes remain
possible.

Scripts may use class tokens for bindings, casts, and safe type checks. These
tokens do not expose class loaders, reflection, resources, or metaclasses.
Explicit calls to Groovy's dispatch plumbing are blocked; ordinary property
syntax and recipe DSL dispatch continue to use those hooks internally.

Installed Java addons are trusted. Addon authors must validate side effects in
their exposed Java methods, including paths before I/O. This policy hardens
supported in-process script access; it cannot guarantee safety of arbitrary
addon implementations or prevent CPU/memory exhaustion from script code.
