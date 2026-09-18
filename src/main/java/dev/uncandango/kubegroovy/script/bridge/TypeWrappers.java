package dev.uncandango.kubegroovy.script.bridge;

import org.codehaus.groovy.runtime.typehandling.GroovyCastException;
import org.jspecify.annotations.Nullable;
import dev.uncandango.kubegroovy.script.type.TypeInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of script-facing type wrappers.
 *
 * The public map is retained for addon compatibility, but its values are now
 * entries carrying the validator and the original factory. This lets explicit
 * Groovy casts use the same conversion functions as the legacy API without
 * making arbitrary Java method calls implicitly coerce values.
 */
public class TypeWrappers {
	public final Map<Class<?>, Object> wrappers = new ConcurrentHashMap<>();

	private record Entry(@Nullable TypeWrapperValidator validator, @Nullable TypeWrapperFactory<?> factory,
			@Nullable DirectTypeWrapperFactory<?> directFactory) {
	}

	public <T> void register(Class<T> target, TypeWrapperValidator validator, TypeWrapperFactory<T> factory) {
		wrappers.put(target, new Entry(validator, factory, null));
	}

	public <T> void register(Class<T> target, TypeWrapperFactory<T> factory) {
		wrappers.put(target, new Entry(null, factory, null));
	}

	public <T> void registerDirect(Class<T> target, TypeWrapperValidator validator, DirectTypeWrapperFactory<T> factory) {
		wrappers.put(target, new Entry(validator, null, factory));
	}

	public <T> void registerDirect(Class<T> target, DirectTypeWrapperFactory<T> factory) {
		wrappers.put(target, new Entry(null, null, factory));
	}

	public boolean hasWrapper(Class<?> target) {
		return target != null && wrappers.containsKey(target);
	}

	public boolean hasWrapper(String targetName) {
		// Groovy AST names nested classes with dots (Outer.Inner) while
		// Class.getName uses '$' (Outer$Inner); match both spellings so
		// explicit casts to nested wrapper targets are rewritten instead of
		// falling through to Groovy's native cast.
		return targetName != null && wrappers.keySet().stream().anyMatch(c ->
			c.getName().equals(targetName) || c.getSimpleName().equals(targetName)
				|| targetName.equals(c.getCanonicalName()) || c.getName().replace('$', '.').equals(targetName));
	}

	/** Convert an explicit Groovy cast through the registered wrapper. */
	public Object coerce(Context cx, Object value, TypeInfo target) {
		if (target == null) {
			throw new GroovyCastException(value, Object.class);
		}
		value = Wrapper.unwrapped(value);

		if (target.asClass().isArray()) {
			return coerceCollection(cx, value, target);
		}

		Object raw = wrappers.get(target.asClass());
		if (raw == null && value == null || target.asClass().isInstance(value)) {
			return value;
		}

		if (!(raw instanceof Entry entry)) {
			throw new GroovyCastException(value, target.asClass());
		}

		var type = target;
		try {
			if (entry.validator != null && !entry.validator.test(value, type)) {
				throw new IllegalArgumentException("value is not valid for " + target);
			}
			Object result = entry.directFactory != null
				? entry.directFactory.wrap(value)
				: entry.factory == null ? null : entry.factory.wrap(cx, value, type);
			if (result != null && !target.asClass().isInstance(result)) {
				throw new IllegalArgumentException("wrapper returned " + result.getClass().getName());
			}
			return result;
		} catch (GroovyCastException ex) {
			throw ex;
		} catch (Throwable ex) {
			throw new GroovyCastException(value, target.asClass(), ex instanceof Exception e ? e : new RuntimeException(ex));
		}
	}

	public Object coerceCollection(Context cx, Object from, TypeInfo target){
		if (from instanceof Object[] arr && java.util.Collection.class.isAssignableFrom(target.asClass())) {
			return toCollection(java.util.Arrays.asList(arr), target.asClass());
		}
		if (from instanceof Iterable<?> it && java.util.Collection.class.isAssignableFrom(target.asClass())) {
			var out = new java.util.ArrayList<>();
			for (var e : it) {
				out.add(e);
			}
			return toCollection(out, target.asClass());
		}
		if (target.asClass().isArray() && from instanceof java.util.Collection<?> coll) {
			var comp = target.componentType().asClass();
			var arr = java.lang.reflect.Array.newInstance(comp, coll.size());
			int i = 0;
			for (var e : coll) {
				java.lang.reflect.Array.set(arr, i++, coerce(cx, e, target.componentType()));
			}
			return arr;
		}
		return from;
	}

	private static Object toCollection(java.util.List<?> list, Class<?> target) {
		if (target.isInstance(list)) {
			return list;
		}
		if (target == java.util.Set.class || target == java.util.HashSet.class) {
			return new java.util.HashSet<>(list);
		}
		if (target == java.util.SortedSet.class || target == java.util.TreeSet.class) {
			return new java.util.TreeSet<>((java.util.Collection) list);
		}
		if (target == java.util.Queue.class || target == java.util.Deque.class || target == java.util.ArrayDeque.class) {
			return new java.util.ArrayDeque<>(list);
		}
		return list;
	}

	/** Convert an explicit Groovy cast through the registered wrapper. */
	public Object coerce(Context cx, Object value, Class<?> target) {
		if (target == null) {
			throw new GroovyCastException(value, Object.class);
		}
		value = Wrapper.unwrapped(value);

		Object raw = wrappers.get(target);
		if (raw == null && value == null || target.isInstance(value)) {
			return value;
		}

		if (!(raw instanceof Entry entry)) {
			throw new GroovyCastException(value, target);
		}

		var type = TypeInfo.of(target);
		try {
			if (entry.validator != null && !entry.validator.test(value, type)) {
				throw new IllegalArgumentException("value is not valid for " + target.getName());
			}
			Object result = entry.directFactory != null
				? entry.directFactory.wrap(value)
				: entry.factory == null ? null : entry.factory.wrap(cx, value, type);
			if (result != null && !target.isInstance(result)) {
				throw new IllegalArgumentException("wrapper returned " + result.getClass().getName());
			}
			return result;
		} catch (GroovyCastException ex) {
			throw ex;
		} catch (Throwable ex) {
			throw new GroovyCastException(value, target, ex instanceof Exception e ? e : new RuntimeException(ex));
		}
	}
}
