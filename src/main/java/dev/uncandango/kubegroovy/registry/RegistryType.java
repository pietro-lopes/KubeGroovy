package dev.uncandango.kubegroovy.registry;

import dev.uncandango.kubegroovy.DevProperties;
import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.JavaWrapper;
import dev.uncandango.kubegroovy.util.Cast;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.neoforged.neoforge.common.util.Lazy;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public record RegistryType<T>(ResourceKey<Registry<T>> key, Class<?> baseClass, TypeInfo type) {
	private static final Map<ResourceKey<?>, RegistryType<?>> KEY_MAP = new Reference2ObjectOpenHashMap<>();
	private static final Map<TypeInfo, RegistryType<?>> TYPE_MAP = new HashMap<>();
	private static final Map<Class<?>, List<RegistryType<?>>> CLASS_MAP = new Reference2ObjectOpenHashMap<>();

	public static synchronized <T> void register(ResourceKey<Registry<T>> key, TypeInfo type) {
		var t = new RegistryType<>(key, type.asClass(), type);
		KEY_MAP.put(key, t);
		if (TYPE_MAP.containsKey(type)) {
			if (DevProperties.get().logRegistryTypes) {
				KubeGroovy.LOGGER.warn("Registry key \"{}\" and \"{}\" share the same class and generics \"{}\", skipping \"{}\"...", key.identifier(), TYPE_MAP.get(type).key().identifier(), type, key.identifier());
			}
		} else {
			TYPE_MAP.put(type, t);
		}
		CLASS_MAP.computeIfAbsent(t.baseClass, c -> new ArrayList<>(1)).add(t);

		if (DevProperties.get().logRegistryTypes) {
			KubeGroovy.LOGGER.info("Registered RegistryType '{}': {}", key.identifier(), type);
		}
	}

	// Lookups must run the scanner before taking the RegistryType monitor: scanning initializes
	// foreign classes, and holding a lock across another thread's <clinit> can deadlock mod construction.
	@Nullable
	public static <T> RegistryType<T> ofKey(ResourceKey<? extends Registry<T>> key) {
		Scanner.startIfNotFrozen();
		synchronized (RegistryType.class) {
			return Cast.to(KEY_MAP.get(key));
		}
	}

	@Nullable
	public static RegistryType<?> ofType(TypeInfo typeInfo) {
		Scanner.startIfNotFrozen();
		synchronized (RegistryType.class) {
			return TYPE_MAP.get(typeInfo);
		}
	}

	@Nullable
	public static <T> RegistryType<T> ofClass(Class<T> type) {
		var regList = allOfClass(type);
		return regList != null && regList.size() == 1 ? regList.getFirst() : null;
	}

	public static <T> List<RegistryType<T>> allOfClass(Class<T> type) {
		Scanner.startIfNotFrozen();
		synchronized (RegistryType.class) {
			return Cast.to(CLASS_MAP.getOrDefault(type, List.of()));
		}
	}

	@Nullable
	public static RegistryType<?> lookup(TypeInfo target) {
		var reg = allOfClass(target.asClass());

		if (reg.size() == 1) {
			return reg.getFirst();
		} else if (!reg.isEmpty()) {
			for (var regType : reg) {
				if (regType.type().equals(target)) {
					return regType;
				}
			}
		}

		return null;
	}

	@Override
	public String toString() {
		return key.identifier() + "=" + type;
	}

	public static class Scanner {
		private static final Lazy<Set<Class<?>>> VALID_TYPES = Lazy.of(() -> {
			Set<Class<?>> set = new HashSet<>();
			set.add(ResourceKey.class);
			set.add(Registry.class);
			var registrar = JavaWrapper.tryLoadClass("dev.architectury.registry.registries.Registrar");
			if (registrar != null) {
				set.add(registrar);
			}
			return set;
		});

		private static final Set<String> CLASSES_TO_SCAN = new HashSet<>();
		private static final Set<String> SCANNED_CLASSES = new HashSet<>();
		private static final Set<String> MODULES_TO_SKIP = Set.of("java.base", "neoforge", "fml_loader", "KubeGroovy");
		private static final Set<String> NAMESPACES_TO_SKIP = Set.of("neoforge", "minecraft");

		// Guards CLASSES_TO_SCAN and SCANNED_CLASSES only. Never hold it while loading or initializing
		// classes: scan() is reached from ResourceKey.<init>, i.e. from inside other mods' <clinit>.
		private static final Object PENDING_LOCK = new Object();

		private static volatile boolean frozen = false;

		public static void freeze() {
			frozen = true;
			synchronized (PENDING_LOCK) {
				SCANNED_CLASSES.clear();
			}
		}

		public static void init() {
			processClass(Stream.of(Registries.class, NeoForgeRegistries.Keys.class));
		}

		private static void startIfNotFrozen() {
			if (frozen) {
				return;
			}

			List<String> pending;
			synchronized (PENDING_LOCK) {
				if (CLASSES_TO_SCAN.isEmpty()) {
					return;
				}
				pending = List.copyOf(CLASSES_TO_SCAN);
				SCANNED_CLASSES.addAll(CLASSES_TO_SCAN);
				CLASSES_TO_SCAN.clear();
			}

			var startTime = Util.getNanos();
			processClass(pending.stream().map(JavaWrapper::tryLoadClass));
			KubeGroovy.LOGGER.debug("Took {} ms to discover registry classes.", (int) ((Util.getNanos() - startTime) / 1_000_000));
		}

		private static void processClass(Stream<Class<?>> classStream) {
			classStream
				.filter(Objects::nonNull)
				.map(Class::getDeclaredFields)
				.flatMap(Stream::of)
				.forEach(field -> {
					try {
						if (!VALID_TYPES.get().contains(field.getType()) || !Modifier.isStatic(field.getModifiers())) {
							return;
						}

						if (!Modifier.isPublic(field.getModifiers())) {
							field.setAccessible(true);
						}

						var value = field.get(null);

						if (value instanceof ResourceKey<?> key) {
							if (field.getGenericType() instanceof ParameterizedType t1
								&& t1.getActualTypeArguments()[0] instanceof ParameterizedType t2) {
								processKey(key, t2, false);
							}
						} else if (value instanceof Registry<?> registry) {
							if (field.getGenericType() instanceof ParameterizedType t1) {
								processKey(registry.key(), t1, true);
							}
						} else if (field.getType().getName().equals("dev.architectury.registry.registries.Registrar")) {
							if (field.getGenericType() instanceof ParameterizedType t1) {
								var method = value.getClass().getDeclaredMethod("key");
								processKey((ResourceKey) method.invoke(value), t1, true);
							}
						}
					} catch (Exception ex) {
						KubeGroovy.LOGGER.error("Error while trying to get registry from field {} from class {}", field.getName(), field.getType().getName(), ex);
					}
				});
		}

		private static void processKey(ResourceKey key, ParameterizedType paramType, boolean checkIfContains) {
			var type = paramType.getActualTypeArguments()[0];
			var typeInfo = TypeInfo.of(type);
			synchronized (RegistryType.class) {
				if (checkIfContains && KEY_MAP.get(key) != null) {
					return;
				}
				register(key, typeInfo);
			}
		}

		public static void scan(Identifier registryName, Identifier location) {
			if (frozen) {
				return;
			}

			if (!registryName.equals(Registries.ROOT_REGISTRY_NAME)) {
				return;
			}

			if (NAMESPACES_TO_SKIP.contains(location.getNamespace())) {
				return;
			}

			var startTime = Util.getNanos();
			var stack = Thread.currentThread().getStackTrace();

			synchronized (PENDING_LOCK) {
				for (var stackTraceElement : stack) {
					var moduleName = stackTraceElement.getModuleName();
					if (moduleName != null && MODULES_TO_SKIP.contains(moduleName)) {
						continue;
					}

					var className = stackTraceElement.getClassName();

					if (SCANNED_CLASSES.contains(className)) {
						continue;
					}

					CLASSES_TO_SCAN.add(className);
				}
			}

			KubeGroovy.LOGGER.debug("Took {} ms to grab stacktrace classes.", (int) ((Util.getNanos() - startTime) / 1_000_000));
		}
	}
}
