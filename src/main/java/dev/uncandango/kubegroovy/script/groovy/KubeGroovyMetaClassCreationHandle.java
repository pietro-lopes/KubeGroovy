package dev.uncandango.kubegroovy.script.groovy;

import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.lang.MetaClassRegistry;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;

/** Installs the narrow KubeGroovy dispatch bridge used by script call sites. */
public final class KubeGroovyMetaClassCreationHandle extends MetaClassRegistry.MetaClassCreationHandle {
	public static final KubeGroovyMetaClassCreationHandle INSTANCE = new KubeGroovyMetaClassCreationHandle();
	private static final String[] INJECTED_TYPES = {
		"net.minecraft.server.MinecraftServer",
		"net.minecraft.client.Minecraft",
		"net.minecraft.world.level.block.Block",
		"net.minecraft.world.level.block.state.BlockBehaviour",
		"net.minecraft.world.level.block.state.BlockState",
		"net.minecraft.world.item.Item",
		"net.minecraft.world.item.ItemStack",
		"net.minecraft.network.chat.MutableComponent",
		"net.minecraft.world.level.Level",
		"net.minecraft.client.multiplayer.ClientLevel",
		"net.minecraft.server.level.ServerLevel",
		"net.minecraft.world.entity.player.Player",
		"net.minecraft.client.player.AbstractClientPlayer",
		"net.minecraft.server.level.ServerPlayer",
		"net.minecraft.world.entity.Entity",
		"net.minecraft.world.entity.LivingEntity",
		"net.minecraft.world.item.crafting.Ingredient",
		"net.minecraft.world.entity.item.ItemEntity",
		"net.minecraft.world.entity.decoration.ItemFrame",
		"net.minecraft.world.Container",
		"net.minecraft.world.item.crafting.RecipeHolder",
		"net.minecraft.server.ReloadableServerResources",
		"net.minecraft.world.item.crafting.RecipeManager",
		"net.minecraft.tags.TagManager",
		"net.minecraft.world.level.material.Fluid",
		"net.minecraft.world.inventory.MenuType",
		"net.minecraft.world.entity.EntityType",
		"net.minecraft.tags.TagLoader",
		"dev.uncandango.kubegroovy.level.CachedLevelBlock",
		"dev.uncandango.kubegroovy.color.SimpleColor",
		"dev.uncandango.kubegroovy.color.SimpleColorWithAlpha",
		"dev.uncandango.kubegroovy.color.NoColor"
	};
	private static volatile boolean installed;

	private KubeGroovyMetaClassCreationHandle() {
	}

	@Override
	protected MetaClass createNormalMetaClass(Class theClass, MetaClassRegistry registry) {
		return new KubeGroovyMetaClass(registry, theClass);
	}

	public static synchronized void install() {
		if (installed) {
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
		for (String name : INJECTED_TYPES) {
			try {
				refresh(Class.forName(name, false, loader));
			} catch (ClassNotFoundException ignored) {
				// Client-only classes are absent from a dedicated server classpath.
			}
		}
	}

	/** Rebind an already-resolved application class to the bridge meta class. */
	public static void refresh(Class<?> type) {
		MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
		registry.removeMetaClass(type);
		var metaClass = new KubeGroovyMetaClass(registry, type);
		metaClass.initialize();
		registry.setMetaClass(type, metaClass);
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
