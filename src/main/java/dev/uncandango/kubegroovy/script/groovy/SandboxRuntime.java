package dev.uncandango.kubegroovy.script.groovy;

import dev.uncandango.kubegroovy.KubeGroovyPaths;
import dev.uncandango.kubegroovy.script.ScriptManager;
import groovy.lang.GroovyObject;
import groovy.lang.MetaMethod;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static dev.uncandango.kubegroovy.script.bridge.Context.CURRENT;

/// Compiler-generated entrypoints. Script source cannot import or call this class.
public final class SandboxRuntime {
	private SandboxRuntime() {}
	private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

	public static boolean inScript() {
		return CURRENT.isBound() || WALKER.walk(frames -> frames.anyMatch(f -> scriptLoader(f.getDeclaringClass().getClassLoader())));
	}

	private static boolean scriptLoader(@Nullable ClassLoader loader) {
		for (var current = loader; current != null; current = current.getParent()) {
			if (current instanceof KubeGroovyClassLoader) return true;
		}
		return false;
	}

	public static Object checkValue(@Nullable Object value) {
		check(value, new IdentityHashMap<>());
		return value;
	}

	private static void check(@Nullable Object value, IdentityHashMap<Object, Boolean> seen) {
		if (value == null || seen.put(value, Boolean.TRUE) != null) return;
		var filter = ScriptManager.getClassFilter();
		var type = value instanceof Class<?> clazz ? clazz : value.getClass();
		if (!filter.isAllowed(type)) throw blocked("value of type " + type.getName());
		try {
			if (value instanceof Path path) KubeGroovyPaths.verifyFilePath(path);
			else if (value instanceof File file) KubeGroovyPaths.verifyFilePath(file.toPath());
		} catch (IOException e) {
			throw new SecurityException("Invalid script path", e);
		}
		if (value.getClass().isArray() && !value.getClass().getComponentType().isPrimitive()) {
			for (int i = 0; i < Array.getLength(value); i++) check(Array.get(value, i), seen);
		} else if (standardContainer(type) && value instanceof Map<?, ?> map) {
			for (var entry : map.entrySet()) { check(entry.getKey(), seen); check(entry.getValue(), seen); }
		} else if (standardContainer(type) && value instanceof Collection<?> collection) {
			for (var entry : collection) check(entry, seen);
		} else if (value instanceof Optional<?> optional) {
			if (optional.isPresent()) check(optional.get(), seen);
		}
	}
	private static boolean standardContainer(Class<?> type) {
		return type.getName().startsWith("java.util.") || type.getName().startsWith("com.google.common.collect.");
	}

	public static Object receiver(@Nullable Object value, Object methodName, boolean spread) {
		var name = String.valueOf(methodName);
		if (value == null) return null;
		checkValue(value);
		if (spread) {
			if (value instanceof Iterable<?> values) for (var entry : values) receiver(entry, name, false);
			else if (value.getClass().isArray()) for (int i = 0; i < Array.getLength(value); i++) receiver(Array.get(value, i), name, false);
			return value;
		}
		var type = value instanceof Class<?> clazz ? clazz : value.getClass();
		if (value instanceof Class<?> && Arrays.stream(Class.class.getMethods()).anyMatch(m -> m.getName().equals(name))
			&& !ScriptManager.getClassFilter().isMemberNameAllowed(Class.class, name)) throw blocked("Class." + name);
		if (!ScriptManager.getClassFilter().isMemberNameAllowed(type, name)) throw blocked(type.getName() + "." + name);
		return value;
	}

	public static String methodName(Object value) {
		var name = String.valueOf(value);
		if (!ScriptManager.getClassFilter().isGlobalMethodNameAllowed(name)) throw blocked("method " + name);
		return name;
	}

	/// Groovy calls these internally to implement ordinary properties and DSL dispatch.
	/// Explicit source calls are rejected by the AST and dynamic-name guards.
	public static boolean plumbing(MetaMethod method) {
		return GroovyObject.class.isAssignableFrom(method.getDeclaringClass().getTheClass())
			&& Set.of("getMetaClass", "setMetaClass", "getProperty", "setProperty", "invokeMethod").contains(method.getName());
	}

	/// Declared fields by name across the hierarchy, nearest declaration first (matches the old getDeclaredField walk
	/// without throwing NoSuchFieldException per miss).
	private static final ClassValue<Map<String, Field>> FIELDS = new ClassValue<>() {
		@Override
		protected Map<String, Field> computeValue(Class<?> type) {
			var fields = new HashMap<String, Field>();
			for (var owner = type; owner != null; owner = owner.getSuperclass()) {
				for (var field : owner.getDeclaredFields()) fields.putIfAbsent(field.getName(), field);
			}
			return Map.copyOf(fields);
		}
	};

	public static Object propertyReceiver(@Nullable Object value, Object propertyName, boolean spread) {
		var name = propertyName(propertyName);
		receiver(value, "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1), spread);
		if (value != null && !spread) {
			var type = value instanceof Class<?> clazz ? clazz : value.getClass();
			var field = FIELDS.get(type).get(name);
			if (field != null && !ScriptManager.getClassFilter().isFieldAllowed(type, field)) throw blocked("field " + type.getName() + "." + name);
		}
		return value;
	}

	public static String propertyName(Object propertyName) {
		var name = String.valueOf(propertyName);
		if (name.isEmpty() || name.equals("metaClass") || name.equals("classLoader") || name.equals("properties")) throw blocked("property " + name);
		return name;
	}

	public static void before(MetaMethod method, @Nullable Object receiver, Object[] arguments) {
		var type = receiver instanceof Class<?> clazz ? clazz : receiver == null ? method.getDeclaringClass().getTheClass() : receiver.getClass();
		if (!ScriptManager.getClassFilter().isMethodAllowed(type, method)) throw blocked(type.getName() + "." + method.getName());
		if ((method.getName().equals("getAt") || method.getName().equals("putAt")) && arguments.length > 0 && arguments[0] instanceof String name && !(receiver instanceof Map<?, ?>)) propertyReceiver(receiver, name, false);
		checkValue(receiver);
		checkValue(arguments);
	}

	public static SecurityException blocked(String member) { return new SecurityException("Script access denied: " + member); }
}
