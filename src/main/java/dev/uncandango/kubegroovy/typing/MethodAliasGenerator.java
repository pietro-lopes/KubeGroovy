package dev.uncandango.kubegroovy.typing;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Collects runtime-backed method renames derived from {@link RemapForGY} and
 * {@link RemapPrefixForGY}, keyed by the receiver type the editor resolves.
 * Feeds {@code aliases.json} for the LivePlugin method-alias augment provider,
 * replacing the former hand-maintained list. Mod discovery and visibility
 * filtering mirror the retired {@code ModGdslGenerator} traversal so the
 * emitted triples stay truthful to the bridge.
 */
public final class MethodAliasGenerator {
	private static final String MIXIN_ANNOTATION = Mixin.class.getName();
	private static final Comparator<Class<?>> CLASS_ORDER = Comparator.comparing(Class::getName);

	private MethodAliasGenerator() {
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

	public record MethodAlias(String type, String from, String to) {
	}

	private record ScannedMod(Set<String> classNames, Set<String> mixinClassNames,
		Map<String, Set<String>> mixinTargets) {
	}

	/** Takes an immutable view of each currently loaded mod's scannable classes. */
	public static List<ModData> discoverLoadedMods() {
		return discoverLoadedMods(MethodAliasGenerator.class.getClassLoader());
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
			KubeGroovy.LOGGER.debug("Skipping unavailable class while collecting method aliases: {}", name, error);
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
			KubeGroovy.LOGGER.debug("Unable to read mixin interfaces while collecting method aliases: {}", className, error);
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

	/**
	 * Collects runtime-backed method renames keyed by the receiver type the
	 * editor resolves. Traversal and visibility filtering mirror the retired
	 * GDSL collector so the emitted triples stay truthful to the bridge
	 * without a hand-maintained list.
	 */
	public static List<MethodAlias> methodAliases(Collection<ModData> mods) {
		var seen = new TreeSet<String>();
		var out = new ArrayList<MethodAlias>();
		for (var mod : mods.stream().sorted(Comparator.comparing(ModData::modId)).toList()) {
			var injectedInterfaces = mod.receiverBridges().stream()
				.flatMap(bridge -> bridge.interfaces().stream())
				.collect(java.util.stream.Collectors.toSet());
			for (var type : mod.classes()) {
				if (type.isAnnotation() || type.isInterface() && injectedInterfaces.contains(type)
					|| isMixinType(type, mod.receiverBridges())) continue;
				if (isHidden(type)) continue;
				for (var method : methods(type, false)) {
					addMethodAlias(seen, out, pluginTypeName(type), method);
				}
			}
			for (var bridge : mod.receiverBridges()) {
				var receiver = bridge.receiverType();
				if (isHidden(receiver)) continue;
				var receiverName = pluginTypeName(receiver);
				for (var iface : bridge.interfaces()) {
					if (isHidden(iface)) continue;
					for (var method : methods(iface, true)) {
						addMethodAlias(seen, out, receiverName, method);
					}
				}
				for (var mixin : bridge.mixins()) {
					if (isHidden(mixin)) continue;
					for (var method : methods(mixin, true)) {
						addMethodAlias(seen, out, receiverName, method);
					}
				}
			}
		}
		return List.copyOf(out);
	}

	private static void addMethodAlias(Set<String> seen, List<MethodAlias> out, String receiverName, Method method) {
		if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic() || method.isBridge()) return;
		if (method.isAnnotationPresent(HideFromGY.class) || isHidden(method.getDeclaringClass())) return;
		if (method.getName().isBlank()) return;
		for (var alias : aliases(method)) {
			if (alias.isBlank() || alias.equals(method.getName())) continue;
			var key = receiverName + "\u0000" + method.getName() + "\u0000" + alias;
			if (seen.add(key)) out.add(new MethodAlias(receiverName, method.getName(), alias));
		}
	}

	private static List<Method> methods(Class<?> type, boolean declared) {
		try {
			var methods = declared ? type.getDeclaredMethods() : type.getMethods();
			return Arrays.stream(methods).sorted(Comparator.comparing(Method::toGenericString)).toList();
		} catch (LinkageError | RuntimeException error) {
			KubeGroovy.LOGGER.debug("Skipping members with unavailable types while collecting method aliases: {}", type.getName(), error);
			return List.of();
		}
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

	private static String pluginTypeName(Class<?> type) {
		var canonicalName = type.getCanonicalName();
		return canonicalName == null ? type.getName().replace('$', '.') : canonicalName;
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

	private static boolean isMixinType(Class<?> type, Collection<ReceiverBridge> bridges) {
		return bridges.stream().anyMatch(bridge -> bridge.mixins().contains(type));
	}
}
