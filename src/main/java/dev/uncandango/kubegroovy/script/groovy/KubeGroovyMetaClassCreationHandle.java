package dev.uncandango.kubegroovy.script.groovy;

import com.google.common.base.Predicates;
import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.lang.MetaClassRegistry;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.Lazy;
import org.objectweb.asm.Type;
import org.spongepowered.asm.mixin.transformer.throwables.IllegalClassLoadError;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
// TODO: REMOVE THIS CLASS, THIS IS OUTDATED, WE ARE NOT USING CUSTOM METACLASS ANYMORE
/** Installs the narrow KubeGroovy dispatch bridge used by script call sites. */
public final class KubeGroovyMetaClassCreationHandle extends MetaClassRegistry.MetaClassCreationHandle {
	public static final KubeGroovyMetaClassCreationHandle INSTANCE = new KubeGroovyMetaClassCreationHandle();

	public static final Lazy<String[]> INJECTED_TYPES_LAZY = Lazy.of(() -> {
		var types = List.of(RemapForGY.class.getName(), RemapPrefixForGY.class.getName(), HideFromGY.class.getName());
		var predicate = Predicates.in(types);
		var injectedTypes = new HashSet<String>();
		for (var data : ModList.get().getAllScanData()) {
			for (var annotation : data.getAnnotations()) {
				if (predicate.test(annotation.annotationType().getClassName())){
//					KubeGroovy.LOGGER.info("Found annotation: " + annotation.annotationType().getClassName());
//					KubeGroovy.LOGGER.info("At class: " + annotation.clazz().getClassName());
//					KubeGroovy.LOGGER.info("At member: " + annotation.memberName());
					injectedTypes.add(annotation.clazz().getClassName());
				}
			}
		}
		return injectedTypes.toArray(String[]::new);
	});

	private static volatile boolean installed;

	private KubeGroovyMetaClassCreationHandle() {
	}

	@Override
	protected MetaClass createNormalMetaClass(Class theClass, MetaClassRegistry registry) {
		return new KubeGroovyMetaClass(registry, theClass);
	}

	public static synchronized void install() {
		// TODO: KEEP THIS HARDCODED UNTIL WE REMOVE THIS CLASS, THIS IS OUTDATED, WE ARE NOT USING CUSTOM METACLASS ANYMORE
		if (true) { // installed
			return;
		}
		MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
		registry.setMetaClassCreationHandle(INSTANCE);
		// Plugins may have caused a Groovy MetaClass to be created before the
		// script context is initialized.  Evict those application classes so the
		// new creation handle is used for the first script call.
		var stale = new java.util.ArrayList<Class<?>>();
		var iterator = registry.iterator();
		while (iterator.hasNext()) {
			Object value = iterator.next();
			if (value instanceof MetaClass metaClass) {
				Class<?> type = metaClass.getTheClass();
				if (type.getName().startsWith("dev.uncandango.kubegroovy.")) {
					stale.add(type);
				}
			}
		}
		for (Class<?> type : stale) {
			registry.removeMetaClass(type);
		}
		installed = true;
	}

	/** Refresh the concrete Minecraft classes which receive KGY interfaces. */
	public static void refreshInjectedTypes() {
		ClassLoader loader = KubeGroovyMetaClassCreationHandle.class.getClassLoader();
		for (String name : INJECTED_TYPES_LAZY.get()) {
			try {
				refresh(Class.forName(name, false, loader));
			} catch (ClassNotFoundException | IllegalClassLoadError | NoClassDefFoundError ignored) {
				// Client-only classes are absent from a dedicated server classpath.
			}
		}
	}

	/** Rebind an already-resolved application class to the bridge meta class. */
	public static void refresh(Class<?> type) {
//		MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
//		registry.removeMetaClass(type);
//		var metaClass = new KubeGroovyMetaClass(registry, type);
//		metaClass.initialize();
//		registry.setMetaClass(type, metaClass);
	}

	/**
	 * Callback arguments can be classes whose Groovy meta class was created by a
	 * plugin before the script context installed the bridge. Refresh only
	 * application types carrying one of the bridge annotations, keeping ordinary
	 * Java dispatch untouched for all other values.
	 */
	public static void refreshIfNeeded(Object value) {
		if (value == null) {
			return;
		}
		Class<?> type = value.getClass();
		if (!needsBridge(type)) {
			return;
		}
		MetaClass current = GroovySystem.getMetaClassRegistry().getMetaClass(type);
		if (!(current instanceof KubeGroovyMetaClass)) {
			refresh(type);
		}
	}

	private static boolean needsBridge(Class<?> type) {
		if (hasBridgeAnnotation(type)) {
			return true;
		}
		for (var method : type.getMethods()) {
			if (method.isAnnotationPresent(RemapForGY.class)
				|| hasBridgeAnnotation(method.getDeclaringClass())) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasBridgeAnnotation(Class<?> type) {
		if (type.getAnnotation(RemapPrefixForGY.class) != null || type.getAnnotation(RemapForGY.class) != null) {
			return true;
		}
		for (Class<?> iface : type.getInterfaces()) {
			if (hasBridgeAnnotation(iface)) {
				return true;
			}
		}
		return type.getSuperclass() != null && type.getSuperclass() != Object.class && hasBridgeAnnotation(type.getSuperclass());
	}
}
