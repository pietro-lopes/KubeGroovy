package dev.uncandango.kubegroovy.script;

import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Either;
import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.holder.HolderWrapper;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugins;
import dev.uncandango.kubegroovy.registry.RegistryType;
import dev.uncandango.kubegroovy.script.bridge.ClassVisibilityContext;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.GroovyConversions;
import dev.uncandango.kubegroovy.script.bridge.NativeJavaClass;
import dev.uncandango.kubegroovy.script.bridge.Scriptable;
import dev.uncandango.kubegroovy.script.bridge.ScriptableObject;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import dev.uncandango.kubegroovy.script.groovy.KubeGroovyClassLoader;
import dev.uncandango.kubegroovy.script.groovy.KubeGroovyMetaClassCreationHandle;
import dev.uncandango.kubegroovy.registry.RegistryKubeEvent;
import dev.uncandango.kubegroovy.util.ID;
import dev.uncandango.kubegroovy.util.JsonUtils;
import dev.uncandango.kubegroovy.util.RegistryAccessContainer;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.util.GroovyScriptEngine;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.AccessibleObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/// Groovy script context: owns a GroovyShell + Binding with Minecraft coercions and class filtering.
public class KubeGroovyContext extends Context {
	public final KubeGroovyContextFactory kgyFactory;
	public final Scriptable topLevelScope;
	public final Binding binding;
	public final GroovyScriptEngine engine;
	private @Nullable Map<String, Either<NativeJavaClass, Boolean>> javaClassCache;

	public KubeGroovyContext(KubeGroovyContextFactory factory) {
		this.kgyFactory = factory;
		KubeGroovyMetaClassCreationHandle.install();
		KubeGroovyMetaClassCreationHandle.refreshInjectedTypes();
		KubeGroovyMetaClassCreationHandle.refresh(RegistryKubeEvent.class);
		this.binding = new Binding();
		var parent = KubeGroovy.class.getClassLoader();
		var config = KubeGroovyClassLoader.makeConfig(factory.manager, factory.getTypeWrappers());
		var loader = new KubeGroovyClassLoader(parent, factory.manager, config);
		try {
			this.engine = new GroovyScriptEngine(factory.manager.scriptType.path.toString(), loader);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		this.topLevelScope = new ScriptableObject();

		var bindingsEvent = new BindingRegistry(this, binding, topLevelScope);

		for (var plugin : KubeGroovyPlugins.getAll()) {
			try {
				plugin.registerBindings(bindingsEvent);
			} catch (Throwable t) {
				getConsole().error("Failed to register bindings from " + plugin.getClass().getName(), t);
			}
		}

		try {
			KubeGroovyPlugins.addSidedBindings(bindingsEvent);
		} catch (Throwable t) {
			getConsole().error("Failed to register sided bindings", t);
		}

		// Mirror bindings into legacy scope map for compat
		for (Map.Entry<String, Object> e : ((Map<String, Object>) binding.getVariables()).entrySet()) {
			addToScope(topLevelScope, e.getKey(), e.getValue());
		}
	}

	public boolean visibleToScripts(String fullClassName, ClassVisibilityContext type) {
		if (type == ClassVisibilityContext.CLASS_IN_PACKAGE || type == ClassVisibilityContext.ARGUMENT) {
			return kgyFactory.manager.isClassAllowed(fullClassName);
		}
		return true;
	}

	public ScriptType getType() {
		return kgyFactory.manager.scriptType;
	}

	public ConsoleGY getConsole() {
		return kgyFactory.manager.scriptType.console;
	}

	public RegistryAccessContainer getRegistries() {
		return kgyFactory.manager.getRegistries();
	}

	@Override
	public Scriptable wrapAsJavaObject(Scriptable scope, Object javaObject, TypeInfo target) {
		if (javaObject instanceof AccessibleObject) {
			getConsole().error("Reflection access denied");
			return new ScriptableObject();
		} else if (javaObject instanceof ClassLoader) {
			getConsole().error("ClassLoader access denied");
			return new ScriptableObject();
		}
		return super.wrapAsJavaObject(scope, javaObject, target);
	}

	@Override
	public int internalConversionWeightLast(Object fromObj, TypeInfo target) {
		var c = target.asClass();
		if (c == Optional.class || c == ResourceKey.class || c == Holder.class || c == TagKey.class || c == Holder.Reference.class) {
			return CONVERSION_TRIVIAL;
		} else if (c != Object.class) {
			var reg = RegistryType.allOfClass(target.asClass());
			if (!reg.isEmpty()) {
				return CONVERSION_TRIVIAL;
			}
		}
		return super.internalConversionWeightLast(fromObj, target);
	}

	public RegistryType<?> lookupRegistryType(TypeInfo type, Object from) {
		var registryType = RegistryType.lookup(type);
		if (registryType == null) {
			throw reportRuntimeError("Can't interpret '" + from + "': no registries for type '" + type + "' found", this);
		}
		return registryType;
	}

	public Registry<?> lookupRegistry(TypeInfo type, Object from) {
		var registryType = lookupRegistryType(type, from);
		var registry = getRegistries().lookup(registryType.key()).orElse(null);
		if (registry == null) {
			throw reportRuntimeError("Can't interpret '" + from + "' as '" + registryType.key().identifier() + "': registry not found", this);
		}
		return registry;
	}

	@Override
	protected Object internalGyToJavaLast(Object from, TypeInfo target) {
		var c = target.asClass();
		if (c == Optional.class) {
			if (from instanceof Optional<?> o) {
				return o;
			}
			return Optional.ofNullable(gyToJava(from, target.param(0)));
		} else if (c == ResourceKey.class) {
			if (from instanceof ResourceKey<?> k) {
				return k;
			}
			var registryType = lookupRegistryType(target.param(0), from);
			var id = ID.mc(from);
			return ResourceKey.create(registryType.key(), id);
		} else if (c == Holder.class) {
			return HolderWrapper.wrap(this, from, target.param(0));
		} else if (c == Holder.Reference.class) {
			return HolderWrapper.wrapRef(this, from, target.param(0));
		} else if (c == HolderSet.class) {
			return HolderWrapper.wrapSet(this, from, target.param(0));
		} else if (c == TagKey.class) {
			if (from instanceof TagKey<?> k) {
				return k;
			}
			var registryType = lookupRegistryType(target.param(0), from);
			var id = ID.mc(from);
			return TagKey.create(registryType.key(), id);
		} else if (AccessibleObject.class.isAssignableFrom(c)) {
			throw throwAsScriptRuntimeEx(new IllegalAccessException("Reflection access denied"), this);
		} else if (ClassLoader.class.isAssignableFrom(c)) {
			throw throwAsScriptRuntimeEx(new IllegalAccessException("ClassLoader access denied"), this);
		} else if (from instanceof Holder<?> holder && c.isInstance(holder.value())) {
			return holder.value();
		} else if (ID.isKey(from)) {
			var reg = RegistryType.lookup(target);
			if (reg != null) {
				return getRegistries()
					.lookup(reg.key())
					.orElseThrow(() -> reportRuntimeError("Can't interpret '%s' as '%s': registry not found".formatted(from, reg.key().identifier()), this))
					.getOptional(ID.mc(from))
					.orElseThrow(() -> reportRuntimeError("Can't interpret '%s' as '%s': entry not found".formatted(from, reg.key().identifier()), this));
			}
		}
		return GroovyConversions.toJava(from, c);
	}

	@Override
	public Object gyToJava(Object from, TypeInfo target) {
		if (from == null) {
			return null;
		}
		// Fast paths for registry types handled in internalGyToJavaLast
		try {
			return internalGyToJavaLast(from, target);
		} catch (RuntimeException e) {
			throw e;
		}
	}

	@Contract("null, false -> null; null, true -> fail")
	@NullUnmarked
	public NativeJavaClass loadJavaClass(@Nullable String name, boolean error) {
		if (name == null || name.equals("null") || name.isEmpty()) {
			if (error) {
				throw reportRuntimeError("Class name can't be empty!", this);
			} else {
				return null;
			}
		}
		if (javaClassCache == null) {
			javaClassCache = new HashMap<>();
		}
		var either = javaClassCache.get(name);
		if (either == null) {
			either = Either.right(false);
			if (!kgyFactory.manager.isClassAllowed(name)) {
				either = Either.right(true);
			} else {
				try {
					var clazz = Class.forName(name, false, KubeGroovy.class.getClassLoader());
					either = Either.left(new NativeJavaClass(this, topLevelScope, clazz));
					getConsole().info("Loaded Java class '%s'".formatted(name));
				} catch (Exception ignored) {
				}
			}
			javaClassCache.put(name, either);
		}
		var l = either.left().orElse(null);
		if (l != null) {
			return l;
		} else if (error) {
			var found = either.right().orElse(false);
			throw reportRuntimeError((found ? "Failed to load Java class '%s': Class is not allowed by class filter!" : "Failed to load Java class '%s': Class could not be found!").formatted(name), this);
		} else {
			return null;
		}
	}

	@Override
	public Object classOf(Object from) {
		if (from instanceof Class<?> c) {
			return c;
		} else if (from instanceof NativeJavaClass c) {
			return c.getClassObject();
		} else {
			return loadJavaClass(String.valueOf(from), true).getClassObject();
		}
	}

	public Map<String, Either<NativeJavaClass, Boolean>> getJavaClassCache() {
		return javaClassCache == null ? Map.of() : Collections.unmodifiableMap(javaClassCache);
	}

	@Override
	public boolean isMapLike(Object from) {
		return super.isMapLike(from) || from instanceof CompoundTag || from instanceof JsonObject;
	}

	@Override
	public Object mapOf(@Nullable Object from, TypeInfo kTarget, TypeInfo vTarget) {
		if (from instanceof CompoundTag tag) {
			var map = new LinkedHashMap<>();
			for (var key : tag.keySet()) {
				map.put(kTarget == TypeInfo.STRING ? key : String.valueOf(gyToJava(key, kTarget)), gyToJava(tag.get(key), vTarget));
			}
			return map;
		} else if (from instanceof JsonObject json) {
			var map = new LinkedHashMap<>();
			for (var entry : json.entrySet()) {
				map.put(kTarget == TypeInfo.STRING ? entry.getKey() : gyToJava(entry.getKey(), kTarget), gyToJava(JsonUtils.toObject(entry.getValue()), vTarget));
			}
			return map;
		} else {
			return super.mapOf(from, kTarget, vTarget);
		}
	}
}
