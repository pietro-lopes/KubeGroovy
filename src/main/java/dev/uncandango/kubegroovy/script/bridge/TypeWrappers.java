package dev.uncandango.kubegroovy.script.bridge;

import org.codehaus.groovy.runtime.typehandling.GroovyCastException;
import org.jspecify.annotations.Nullable;

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
