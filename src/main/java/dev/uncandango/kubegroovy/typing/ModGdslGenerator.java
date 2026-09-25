package dev.uncandango.kubegroovy.typing;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.TypeWrappers;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import dev.uncandango.kubegroovy.script.type.WithTypeInfo;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.transformer.throwables.IllegalClassLoadError;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Builds per-mod GDSL contributors for members exposed by KubeGroovy's Groovy bridge. */
public final class ModGdslGenerator {
	private static final String MIXIN_ANNOTATION = Mixin.class.getName();
	private static final String OBJECT = Object.class.getName();
	private static final String OBJECT_ARRAY = Object[].class.getTypeName();
	private static final String OBJECT_LIST = "java.util.List<java.lang.Object>";
	private static final Comparator<Class<?>> CLASS_ORDER = Comparator.comparing(Class::getName);

	private ModGdslGenerator() {
	}

	public record ReceiverBridge(Class<?> receiverType, List<Class<?>> interfaces, List<Class<?>> mixins) {
		public ReceiverBridge {
			interfaces = interfaces == null ? List.of() : interfaces.stream().distinct().sorted(CLASS_ORDER).toList();
			mixins = mixins == null ? List.of() : mixins.stream().distinct().sorted(CLASS_ORDER).toList();
		}
	}

	public record ModData(String modId, List<Class<?>> classes, List<ReceiverBridge> receiverBridges) {
		public ModData {
			classes = classes == null ? List.of() : classes.stream().distinct().sorted(CLASS_ORDER).toList();
			receiverBridges = receiverBridges == null ? List.of() : receiverBridges.stream()
				.sorted(Comparator.comparing(bridge -> bridge.receiverType().getName())).toList();
		}
	}

	private record ScannedMod(Set<String> classNames, Set<String> mixinClassNames,
		Map<String, Set<String>> mixinTargets) {
	}

	private record Member(String name, String type, List<String> parameters, List<String> typeParameters, boolean isStatic,
		boolean constructor) {
	}

	private record Property(String name, String type, boolean isStatic) {
	}

	public record LivePluginMember(String receiverType, String name, String returnType, List<String> parameters,
		List<String> typeParameters, boolean isStatic, boolean constructor) {
		public LivePluginMember {
			parameters = List.copyOf(parameters);
			typeParameters = List.copyOf(typeParameters);
		}
	}

	public record LivePluginProperty(String receiverType, String name, String type, boolean isStatic) {
	}

	public record LivePluginSuppressedMethod(String receiverType, String name, List<String> parameters) {
		public LivePluginSuppressedMethod {
			parameters = List.copyOf(parameters);
		}
	}

	public record LivePluginApi(List<LivePluginMember> members, List<LivePluginProperty> properties,
		List<LivePluginSuppressedMethod> suppressedMethods) {
		public LivePluginApi {
			members = List.copyOf(members);
			properties = List.copyOf(properties);
			suppressedMethods = List.copyOf(suppressedMethods);
		}
	}

	private record CollectedApi(TreeMap<String, TreeMap<String, Member>> contributors,
		TreeMap<String, TreeMap<String, Property>> properties,
		TreeMap<String, TreeMap<String, LivePluginSuppressedMethod>> suppressedMethods) {
	}

	/** Takes an immutable view of each currently loaded mod's scannable classes. */
	public static List<ModData> discoverLoadedMods() {
		return discoverLoadedMods(ModGdslGenerator.class.getClassLoader());
	}

	/** Class-loader overload makes side-specific and missing-class behavior testable without loading a game. */
	public static List<ModData> discoverLoadedMods(ClassLoader loader) {
		var modList = ModList.get();
		var loadedIds = new TreeSet<String>();
		for (IModInfo mod : modList.getMods()) loadedIds.add(mod.getModId());
		var scans = new TreeMap<String, ScannedMod>();
		for (var id : loadedIds) scans.put(id, new ScannedMod(new TreeSet<>(), new TreeSet<>(), new TreeMap<>()));

		for (var scanData : modList.getAllScanData()) {
			var ids = scanData.getIModInfoData().stream()
				.flatMap(info -> info.getMods().stream())
				.map(IModInfo::getModId)
				.filter(loadedIds::contains)
				.distinct()
				.sorted()
				.toList();
			if (ids.isEmpty()) continue;
			var classes = scanData.getClasses().stream().map(data -> data.clazz().getClassName()).toList();
			var mixins = scanMixinTargets(scanData);
			for (var id : ids) {
				var scan = scans.get(id);
				scan.classNames().addAll(classes);
				scan.mixinClassNames().addAll(mixins.classNames());
				mixins.targets().forEach((mixin, targets) -> scan.mixinTargets().put(mixin, targets));
			}
		}

		var result = new ArrayList<ModData>(scans.size());
		for (var entry : scans.entrySet()) {
			var modId = entry.getKey();
			var scan = entry.getValue();
			var mixinNames = scan.mixinClassNames();
			var regularClassNames = scan.classNames().stream().filter(name -> !mixinNames.contains(name)).toList();
			var classes = loadClasses(regularClassNames, loader);
			var receiverBridges = new TreeMap<String, BridgeBuilder>();

			for (var mixinEntry : scan.mixinTargets().entrySet()) {
				var mixin = loadClass(mixinEntry.getKey(), loader);
				var mixinInterfaces = interfaceNamesOf(mixinEntry.getKey(), loader);
				if (mixin != null) {
					for (var iface : interfacesOf(mixin)) mixinInterfaces.add(iface.getName());
				}
				for (var targetName : mixinEntry.getValue()) {
					var target = loadClass(targetName, loader);
					if (target == null) continue;
					var bridge = receiverBridges.computeIfAbsent(target.getName(), ignored -> new BridgeBuilder(target));
					for (var ifaceName : mixinInterfaces) {
						var iface = loadClass(ifaceName, loader);
						if (iface != null && iface.isInterface()) bridge.interfaces.add(iface);
					}
					if (mixin != null) bridge.mixins.add(mixin);
				}
			}
			var bridges = receiverBridges.values().stream().map(BridgeBuilder::build).toList();
			result.add(new ModData(modId, classes, bridges));
		}
		return List.copyOf(result);
	}

	private static final class BridgeBuilder {
		private final Class<?> receiver;
		private final Set<Class<?>> interfaces = new LinkedHashSet<>();
		private final Set<Class<?>> mixins = new LinkedHashSet<>();

		private BridgeBuilder(Class<?> receiver) {
			this.receiver = receiver;
		}

		private ReceiverBridge build() {
			return new ReceiverBridge(receiver, List.copyOf(interfaces), List.copyOf(mixins));
		}
	}

	/** Resolves class names without initialization and skips classes unavailable on this runtime side. */
	public static List<Class<?>> loadClasses(Collection<String> classNames, ClassLoader loader) {
		var result = new ArrayList<Class<?>>();
		for (var name : classNames.stream().distinct().sorted().toList()) {
			var type = loadClass(name, loader);
			if (type != null) result.add(type);
		}
		return List.copyOf(result);
	}

	private static Class<?> loadClass(String name, ClassLoader loader) {
		try {
			return Class.forName(name, false, loader);
		} catch (ClassNotFoundException | LinkageError | SecurityException | IllegalClassLoadError error) {
			KubeGroovy.LOGGER.debug("Skipping unavailable class while generating mod GDSL: {}", name, error);
			return null;
		}
	}

	private static Set<String> interfaceNamesOf(String className, ClassLoader loader) {
		var interfaces = new TreeSet<String>();
		var resourceName = className.replace('.', '/') + ".class";
		try (InputStream input = loader.getResourceAsStream(resourceName)) {
			if (input != null) {
				var reader = new ClassReader(input);
				for (var iface : reader.getInterfaces()) interfaces.add(iface.replace('/', '.'));
			}
		} catch (IOException | RuntimeException error) {
			KubeGroovy.LOGGER.debug("Unable to read mixin interfaces while generating mod GDSL: {}", className, error);
		}
		return interfaces;
	}

	private record ScannedMixins(Set<String> classNames, Map<String, Set<String>> targets) {
	}

	private static ScannedMixins scanMixinTargets(ModFileScanData scanData) {
		var classNames = new TreeSet<String>();
		var result = new TreeMap<String, Set<String>>();
		for (var annotation : scanData.getAnnotations()) {
			if (annotation.targetType() != ElementType.TYPE || !MIXIN_ANNOTATION.equals(annotation.annotationType().getClassName())) continue;
			var mixinName = annotation.clazz().getClassName();
			classNames.add(mixinName);
			var targets = new TreeSet<String>();
			collectClassNames(annotation.annotationData().get("value"), targets);
			collectClassNames(annotation.annotationData().get("targets"), targets);
			if (!targets.isEmpty()) result.put(mixinName, targets);
		}
		return new ScannedMixins(classNames, result);
	}

	private static void collectClassNames(Object value, Collection<String> out) {
		if (value == null) return;
		if (value instanceof Type type) {
			if (type.getSort() == Type.OBJECT) out.add(type.getClassName());
			return;
		}
		if (value instanceof String name) {
			var normalized = name.replace('/', '.');
			if (normalized.startsWith("L") && normalized.endsWith(";")) normalized = normalized.substring(1, normalized.length() - 1);
			if (!normalized.isBlank()) out.add(normalized);
			return;
		}
		if (value instanceof Iterable<?> iterable) {
			for (var item : iterable) collectClassNames(item, out);
			return;
		}
		if (value instanceof Object[] array) {
			for (var item : array) collectClassNames(item, out);
		}
	}

	private static Set<Class<?>> interfacesOf(Class<?> type) {
		var result = new LinkedHashSet<Class<?>>();
		var queue = new ArrayDeque<Class<?>>();
		queue.add(type);
		while (!queue.isEmpty()) {
			var current = queue.removeFirst();
			for (var iface : current.getInterfaces()) {
				if (result.add(iface)) queue.addLast(iface);
			}
			if (current.getSuperclass() != null && current.getSuperclass() != Object.class) queue.addLast(current.getSuperclass());
		}
		return result;
	}

	public static Map<String, String> renderAll(Collection<ModData> mods, Collection<TypeWrappers> wrapperRegistries) {
		var out = new TreeMap<String, String>();
		for (var mod : mods.stream().sorted(Comparator.comparing(ModData::modId)).toList()) {
			if (mod.modId() == null || !mod.modId().matches("[a-z0-9_.-]+") || mod.modId().equals(".") || mod.modId().equals("..")) {
				throw new IllegalArgumentException("Invalid loaded mod ID for GDSL filename: " + mod.modId());
			}
			out.put(mod.modId(), render(mod, wrapperRegistries));
		}
		return Collections.unmodifiableMap(new LinkedHashMap<>(out));
	}

	public static String render(ModData mod, Collection<TypeWrappers> wrapperRegistries) {
		var wrappers = wrapperRegistries == null || wrapperRegistries.isEmpty()
			? List.of(new TypeWrappers()) : List.copyOf(wrapperRegistries);
		var collected = collect(mod, wrappers);
		var contributors = collected.contributors();
		var properties = collected.properties();

		var out = new StringBuilder();
		out.append("// ").append(KubeGroovyTypingGenerator.GENERATED_MARKER).append('\n');
		out.append("// Mod: ").append(KubeGroovyTypingGenerator.groovy(mod.modId())).append("\n");
		out.append("// Activate this file in IntelliJ's Groovy settings after generation.\n\n");
		for (var contributor : contributors.entrySet()) {
			out.append("contributor(context(ctype: '").append(KubeGroovyTypingGenerator.groovy(contributor.getKey())).append("')) {\n");
			for (var member : contributor.getValue().values()) renderMember(out, member);
			for (var property : properties.getOrDefault(contributor.getKey(), new TreeMap<>()).values()) {
				out.append("  property(name: '").append(KubeGroovyTypingGenerator.groovy(property.name())).append("', type: '")
					.append(KubeGroovyTypingGenerator.groovy(property.type())).append('\'');
				if (property.isStatic()) out.append(", isStatic: true");
				out.append(")\n");
			}
			out.append("}\n\n");
		}
		return out.toString();
	}

	/**
	 * Describes transformed members only for binding, registered event, and event target types. Keeping
	 * unrelated scanned classes out of the Kotlin script avoids enormous LivePlugin initializers.
	 */
	public static LivePluginApi describeForLivePlugin(Collection<ModData> mods, Collection<TypeWrappers> wrapperRegistries,
		Collection<String> receiverTypeNames) {
		var wrappers = wrapperRegistries == null || wrapperRegistries.isEmpty()
			? List.of(new TypeWrappers()) : List.copyOf(wrapperRegistries);
		var receiverTypes = receiverTypeNames == null ? Set.<String>of() : Set.copyOf(receiverTypeNames);
		var members = new TreeMap<String, TreeMap<String, LivePluginMember>>();
		var properties = new TreeMap<String, TreeMap<String, LivePluginProperty>>();
		var suppressed = new TreeMap<String, TreeMap<String, LivePluginSuppressedMethod>>();
		for (var mod : mods.stream().sorted(Comparator.comparing(ModData::modId)).toList()) {
			var collected = collect(mod, wrappers, receiverTypes);
			collected.contributors().forEach((receiver, receiverMembers) -> {
				for (var entry : receiverMembers.entrySet()) {
					var member = entry.getValue();
					var psiReceiver = pluginTypeName(receiver);
					members.computeIfAbsent(psiReceiver, ignored -> new TreeMap<>()).putIfAbsent(entry.getKey(),
						new LivePluginMember(psiReceiver, member.name(), pluginTypeName(member.type()),
							member.parameters().stream().map(ModGdslGenerator::pluginTypeName).toList(),
							member.typeParameters(), member.isStatic(), member.constructor()));
				}
			});
			collected.properties().forEach((receiver, receiverProperties) -> {
				for (var entry : receiverProperties.entrySet()) {
					var property = entry.getValue();
					var psiReceiver = pluginTypeName(receiver);
					properties.computeIfAbsent(psiReceiver, ignored -> new TreeMap<>()).putIfAbsent(entry.getKey(),
						new LivePluginProperty(psiReceiver, property.name(), pluginTypeName(property.type()), property.isStatic()));
				}
			});
			collected.suppressedMethods().forEach((receiver, receiverMethods) ->
				suppressed.computeIfAbsent(receiver, ignored -> new TreeMap<>()).putAll(receiverMethods));
		}
		return new LivePluginApi(
			members.values().stream().flatMap(map -> map.values().stream()).toList(),
			properties.values().stream().flatMap(map -> map.values().stream()).toList(),
			suppressed.values().stream().flatMap(map -> map.values().stream()).toList()
		);
	}

	private static CollectedApi collect(ModData mod, Collection<TypeWrappers> wrappers) {
		return collect(mod, wrappers, null);
	}

	private static CollectedApi collect(ModData mod, Collection<TypeWrappers> wrappers, Set<String> receiverTypes) {
		var contributors = new TreeMap<String, TreeMap<String, Member>>();
		var properties = new TreeMap<String, TreeMap<String, Property>>();
		var suppressedMethods = new TreeMap<String, TreeMap<String, LivePluginSuppressedMethod>>();
		var injectedInterfaces = mod.receiverBridges().stream()
			.flatMap(bridge -> bridge.interfaces().stream())
			.collect(java.util.stream.Collectors.toSet());

		for (var type : mod.classes()) {
			if (receiverTypes != null && !receiverTypes.contains(type.getName())) continue;
			if (type.isAnnotation() || type.isInterface() && injectedInterfaces.contains(type)
				|| isMixinType(type, mod.receiverBridges())) continue;
			if (isHidden(type)) {
				suppressMethods(suppressedMethods, type, methods(type, false));
				suppressConstructors(suppressedMethods, type, constructors(type));
				continue;
			}
			for (var method : methods(type, false)) {
				addMethod(contributors, properties, suppressedMethods, type, method, false, wrappers);
			}
			for (var constructor : constructors(type)) {
				addConstructor(contributors, type, constructor, wrappers);
			}
		}

		for (var bridge : mod.receiverBridges()) {
			var receiver = bridge.receiverType();
			if (receiverTypes != null && !receiverTypes.contains(receiver.getName())) continue;
			if (isHidden(receiver)) {
				suppressMethods(suppressedMethods, receiver, methods(receiver, false));
				suppressConstructors(suppressedMethods, receiver, constructors(receiver));
				continue;
			}
			for (var iface : bridge.interfaces()) {
				if (isHidden(iface)) {
					suppressMethods(suppressedMethods, receiver, methods(iface, true));
					continue;
				}
				for (var method : methods(iface, true)) {
					addMethod(contributors, properties, suppressedMethods, receiver, method, true, wrappers);
				}
			}
			for (var mixin : bridge.mixins()) {
				if (isHidden(mixin)) {
					suppressMethods(suppressedMethods, receiver, methods(mixin, true));
					continue;
				}
				for (var method : methods(mixin, true)) {
					addMethod(contributors, properties, suppressedMethods, receiver, method, false, wrappers);
				}
			}
		}
		return new CollectedApi(contributors, properties, suppressedMethods);
	}

	private static List<Method> methods(Class<?> type, boolean declared) {
		try {
			var methods = declared ? type.getDeclaredMethods() : type.getMethods();
			return Arrays.stream(methods).sorted(Comparator.comparing(Method::toGenericString)).toList();
		} catch (LinkageError | RuntimeException error) {
			KubeGroovy.LOGGER.debug("Skipping members with unavailable types while generating mod GDSL: {}", type.getName(), error);
			return List.of();
		}
	}

	private static List<Constructor<?>> constructors(Class<?> type) {
		try {
			return Arrays.stream(type.getConstructors()).sorted(Comparator.comparing(Constructor::toGenericString)).toList();
		} catch (LinkageError | RuntimeException error) {
			KubeGroovy.LOGGER.debug("Skipping constructors with unavailable types while generating mod GDSL: {}", type.getName(), error);
			return List.of();
		}
	}

	private static boolean isMixinType(Class<?> type, Collection<ReceiverBridge> bridges) {
		return bridges.stream().anyMatch(bridge -> bridge.mixins().contains(type));
	}

	private static void addMethod(Map<String, TreeMap<String, Member>> contributors,
		Map<String, TreeMap<String, Property>> properties,
		Map<String, TreeMap<String, LivePluginSuppressedMethod>> suppressedMethods, Class<?> receiver, Method method,
		boolean bridgeApi, Collection<TypeWrappers> wrappers) {
		if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic() || method.isBridge()) return;
		if (method.isAnnotationPresent(HideFromGY.class) || isHidden(method.getDeclaringClass())) {
			addSuppressedMethod(suppressedMethods, receiver, method.getName(), method.getParameterTypes());
			return;
		}

		try {
			var parameters = WithTypeInfo.generateParamsTypeInfo(method, receiver, false);
			var methodParameters = method.getParameterTypes();
			int firstArgument = hasInjectedContext(methodParameters) ? 1 : 0;
			if (firstArgument != 0) parameters = Arrays.copyOfRange(parameters, 1, parameters.length);
			var returnType = WithTypeInfo.generateReturnTypeInfo(method, receiver).toString();
			var aliases = aliases(method);
			var names = aliases.isEmpty() ? List.of(method.getName()) : aliases;
			boolean emitBase = bridgeApi || !aliases.isEmpty() || firstArgument != 0;
			boolean emitWrapper = shouldWrap(parameters, wrappers);
			if (!emitBase && !emitWrapper) return;
			if (firstArgument != 0 || aliases.stream().anyMatch(alias -> !alias.equals(method.getName()))) {
				addSuppressedMethod(suppressedMethods, receiver, method.getName(), method.getParameterTypes());
			}

			for (var name : names) {
				if (name.isBlank()) continue;
				var renderedParameters = parameterNames(parameters);
				if (emitBase) {
					addMember(contributors, receiver, new Member(name, returnType, renderedParameters,
						Arrays.stream(method.getTypeParameters()).map(java.lang.reflect.TypeVariable::getName).toList(),
						Modifier.isStatic(method.getModifiers()), false));
				}
				if (emitWrapper) {
					addMember(contributors, receiver, new Member(name, returnType, wrapperParameterNames(parameters, wrappers),
						Arrays.stream(method.getTypeParameters()).map(java.lang.reflect.TypeVariable::getName).toList(),
						Modifier.isStatic(method.getModifiers()), false));
				}
				var property = property(name, parameters, method.getReturnType(), returnType, Modifier.isStatic(method.getModifiers()));
				if (property != null) properties.computeIfAbsent(receiver.getName(), ignored -> new TreeMap<>()).putIfAbsent(property.name(), property);
			}
		} catch (Throwable error) {
			KubeGroovy.LOGGER.debug("Unable to describe method for generated GDSL: {}.{}", receiver.getName(), method.getName(), error);
		}
	}

	private static void addConstructor(Map<String, TreeMap<String, Member>> contributors, Class<?> receiver,
		Constructor<?> constructor, Collection<TypeWrappers> wrappers) {
		if (!Modifier.isPublic(constructor.getModifiers()) || constructor.isSynthetic()
			|| constructor.isAnnotationPresent(HideFromGY.class) || isHidden(constructor.getDeclaringClass())) return;
		try {
			var parameters = WithTypeInfo.generateParamsTypeInfo(constructor, receiver, false);
			if (!shouldWrap(parameters, wrappers)) return;
			var member = new Member(receiver.getSimpleName(), receiver.getName(), wrapperParameterNames(parameters, wrappers),
				List.of(), false, true);
			addMember(contributors, receiver, member);
		} catch (Throwable error) {
			KubeGroovy.LOGGER.debug("Unable to describe constructor for generated GDSL: {}", receiver.getName(), error);
		}
	}

	private static void suppressMethods(Map<String, TreeMap<String, LivePluginSuppressedMethod>> suppressedMethods,
		Class<?> receiver, Collection<Method> methods) {
		for (var method : methods) {
			if (Modifier.isPublic(method.getModifiers())) {
				addSuppressedMethod(suppressedMethods, receiver, method.getName(), method.getParameterTypes());
			}
		}
	}

	private static void suppressConstructors(Map<String, TreeMap<String, LivePluginSuppressedMethod>> suppressedMethods,
		Class<?> receiver, Collection<Constructor<?>> constructors) {
		for (var constructor : constructors) {
			if (Modifier.isPublic(constructor.getModifiers())) {
				addSuppressedMethod(suppressedMethods, receiver, receiver.getSimpleName(), constructor.getParameterTypes());
			}
		}
	}

	private static void addSuppressedMethod(Map<String, TreeMap<String, LivePluginSuppressedMethod>> suppressedMethods,
		Class<?> receiver, String name, Class<?>[] parameterTypes) {
		var rawParameterTypes = Arrays.stream(parameterTypes).map(ModGdslGenerator::pluginTypeName).toList();
		var suppressedKey = name + "(" + String.join(",", rawParameterTypes) + ")";
		suppressedMethods.computeIfAbsent(receiver.getName(), ignored -> new TreeMap<>()).putIfAbsent(suppressedKey,
			new LivePluginSuppressedMethod(pluginTypeName(receiver), name, rawParameterTypes));
	}

	private static List<String> aliases(Method method) {
		var aliases = new LinkedHashSet<String>();
		var remap = method.getAnnotation(RemapForGY.class);
		if (remap != null && !remap.value().isBlank()) aliases.add(remap.value());
		var prefix = prefixFor(method.getDeclaringClass());
		if (prefix != null && !prefix.value().isBlank() && method.getName().startsWith(prefix.value())) {
			aliases.add(method.getName().substring(prefix.value().length()));
		}
		return List.copyOf(aliases);
	}

	private static boolean hasInjectedContext(Class<?>[] parameters) {
		if (parameters.length == 0) return false;
		return Context.class.isAssignableFrom(parameters[0])
			|| isRecipeMatchContext(parameters[0]);
	}

	private static boolean isRecipeMatchContext(Class<?> type) {
		return RecipeMatchContext.class.isAssignableFrom(type);
	}

	private static boolean shouldWrap(TypeInfo[] parameters, Collection<TypeWrappers> wrappers) {
		return !wrapperParameterNames(parameters, wrappers).equals(parameterNames(parameters));
	}

	private static boolean hasAnyWrapper(Collection<TypeWrappers> wrappers, TypeInfo type) {
		for (var registry : wrappers) {
			if (registry.hasAnyWrapper(type)) return true;
		}
		return false;
	}

	private static List<String> parameterNames(TypeInfo[] parameters) {
		var result = new ArrayList<String>(parameters.length);
		for (var parameter : parameters) result.add(parameter.toString());
		return List.copyOf(result);
	}

	private static List<String> wrapperParameterNames(TypeInfo[] parameters, Collection<TypeWrappers> wrappers) {
		var result = new ArrayList<String>(parameters.length);
		for (int i = 0; i < parameters.length; i++) {
			var parameter = parameters[i];
			if (parameter.asClass().isArray()) {
				var component = parameter.componentType();
				if (hasAnyWrapper(wrappers, component)) {
					result.add(OBJECT_LIST);
				} else if (i == parameters.length - 1) {
					result.add(OBJECT_ARRAY);
				} else {
					result.add("java.util.List<" + component + ">");
				}
			} else if (hasAnyWrapper(wrappers, parameter)) {
				result.add(OBJECT);
			} else {
				result.add(parameter.toString());
			}
		}
		return List.copyOf(result);
	}

	private static void addMember(Map<String, TreeMap<String, Member>> contributors, Class<?> receiver, Member member) {
		var signature = member.name() + "(" + String.join(",", member.parameters()) + ")|" + member.isStatic() + "|" + member.constructor();
		contributors.computeIfAbsent(receiver.getName(), ignored -> new TreeMap<>()).putIfAbsent(signature, member);
	}

	private static void renderMember(StringBuilder out, Member member) {
		out.append("  method(name: '").append(KubeGroovyTypingGenerator.groovy(member.name())).append("', type: '")
			.append(KubeGroovyTypingGenerator.groovy(member.type())).append('\'');
		if (!member.parameters().isEmpty()) {
			out.append(", params: [");
			for (int i = 0; i < member.parameters().size(); i++) {
				if (i > 0) out.append(", ");
				out.append("arg").append(i).append(": '")
					.append(KubeGroovyTypingGenerator.groovy(member.parameters().get(i))).append('\'');
			}
			out.append(']');
		}
		if (member.isStatic()) out.append(", isStatic: true");
		if (member.constructor()) out.append(", constructor: true");
		out.append(")\n");
	}

	private static Property property(String name, TypeInfo[] parameters, Class<?> returnClass,
		String returnType, boolean isStatic) {
		if (parameters.length == 0 && name.startsWith("get") && name.length() > 3 && returnClass != void.class && returnClass != Void.class) {
			return new Property(decap(name.substring(3)), returnType, isStatic);
		}
		if (parameters.length == 0 && name.startsWith("is") && name.length() > 2 && (returnClass == boolean.class || returnClass == Boolean.class)) {
			return new Property(decap(name.substring(2)), returnType, isStatic);
		}
		if (parameters.length == 1 && name.startsWith("set") && name.length() > 3) {
			return new Property(decap(name.substring(3)), parameters[0].toString(), isStatic);
		}
		return null;
	}

	private static String decap(String value) {
		return value.isEmpty() ? value : Character.toLowerCase(value.charAt(0)) + value.substring(1);
	}

	private static String pluginTypeName(String typeName) {
		return typeName.replace('$', '.');
	}

	private static String pluginTypeName(Class<?> type) {
		var canonicalName = type.getCanonicalName();
		return canonicalName == null ? pluginTypeName(type.getName()) : canonicalName;
	}

	private static RemapPrefixForGY prefixFor(Class<?> type) {
		var prefix = type.getAnnotation(RemapPrefixForGY.class);
		if (prefix != null) return prefix;
		for (var iface : type.getInterfaces()) {
			prefix = prefixFor(iface);
			if (prefix != null) return prefix;
		}
		return type.getSuperclass() == null ? null : prefixFor(type.getSuperclass());
	}

	private static boolean isHidden(Class<?> type) {
		if (type == null || type == Object.class) return false;
		if (type.isAnnotationPresent(HideFromGY.class)) return true;
		for (var iface : type.getInterfaces()) if (isHidden(iface)) return true;
		return isHidden(type.getSuperclass());
	}
}
