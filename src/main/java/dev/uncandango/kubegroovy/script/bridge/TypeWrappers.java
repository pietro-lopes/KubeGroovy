package dev.uncandango.kubegroovy.script.bridge;

import dev.uncandango.kubegroovy.registry.RegistryType;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.codehaus.groovy.runtime.typehandling.GroovyCastException;
import org.jspecify.annotations.Nullable;
import dev.uncandango.kubegroovy.script.type.TypeInfo;

import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of script-facing type wrappers.
 *
 * The public map is retained for addon compatibility, but its values are now
 * entries carrying the validator and the original factory. Explicit Groovy
 * casts and generated method and constructor bridges share these conversions,
 * including eligible values nested inside supported containers.
 */
public class TypeWrappers {
	public final Map<Class<?>, Entry> wrappers = new ConcurrentHashMap<>();
	public static final List<TypeInfo> COERSE_ONLY_CHARSEQUENCE = List.of(TypeInfo.of(CharSequence.class));

	public record Entry(TypeWrapperValidator validator, @Nullable TypeWrapperFactory<?> factory,
						List<TypeInfo> explicitCoercions) {
	}

	public <T> void register(Class<T> target, TypeWrapperValidator validator, TypeWrapperFactory<T> factory, boolean coercesCharSequence) {
		wrappers.put(target, new Entry(validator, factory, coercesCharSequence ? COERSE_ONLY_CHARSEQUENCE : List.of()));
	}

	public <T> void register(Class<T> target, TypeWrapperValidator validator, TypeWrapperFactory<T> factory, List<TypeInfo> explicitCoercions) {
		wrappers.put(target, new Entry(validator, factory, Objects.requireNonNull(explicitCoercions)));
	}

	public <T> void register(Class<T> target, TypeWrapperFactory<T> factory, boolean coercesCharSequence) {
		wrappers.put(target, new Entry(TypeWrapperValidator.ALWAYS_VALID, factory, coercesCharSequence ? COERSE_ONLY_CHARSEQUENCE : List.of()));
	}

	public <T> void register(Class<T> target, TypeWrapperFactory<T> factory, List<TypeInfo> explicitCoercions) {
		wrappers.put(target, new Entry(TypeWrapperValidator.ALWAYS_VALID, factory, Objects.requireNonNull(explicitCoercions)));
	}

	public <T> void registerDirect(Class<T> target, TypeWrapperValidator validator, DirectTypeWrapperFactory<T> factory, boolean coercesCharSequence) {
		wrappers.put(target, new Entry(validator, factory, coercesCharSequence ? COERSE_ONLY_CHARSEQUENCE : List.of()));
	}

	public <T> void registerDirect(Class<T> target, TypeWrapperValidator validator, DirectTypeWrapperFactory<T> factory, List<TypeInfo> explicitCoercions) {
		wrappers.put(target, new Entry(validator, factory, Objects.requireNonNull(explicitCoercions)));
	}

	public <T> void registerDirect(Class<T> target, DirectTypeWrapperFactory<T> factory, boolean coercesCharSequence) {
		wrappers.put(target, new Entry(TypeWrapperValidator.ALWAYS_VALID, factory, coercesCharSequence ? COERSE_ONLY_CHARSEQUENCE : List.of()));
	}

	public <T> void registerDirect(Class<T> target, DirectTypeWrapperFactory<T> factory, List<TypeInfo> explicitCoercions) {
		wrappers.put(target, new Entry(TypeWrapperValidator.ALWAYS_VALID, factory, explicitCoercions));
	}

	private static final Set<TypeInfo> INTERNAL_WRAPPERS = Set.of(
			TypeInfo.of(Optional.class),
			TypeInfo.of(ResourceKey.class),
			TypeInfo.of(Holder.class),
			TypeInfo.of(Holder.Reference.class),
			TypeInfo.of(HolderSet.class),
			TypeInfo.of(TagKey.class)
	);

	public boolean hasAnyWrapper(TypeInfo target) {
		if (hasWrappedContainer(target)) {
			return true;
		}
		if (target instanceof TypeWrapperFactory<?>) {
			return true;
		}

		var reg = RegistryType.ofType(target);
		if (reg != null) {
			return true;
		}

		for (var wrapper : INTERNAL_WRAPPERS) {
			if (wrapper.asClass() == target.asClass()) {
				return true;
			}
		}

		var wrapper = wrappers.get(target.asClass());
		return wrapper != null;
	}

	/** Only inspect containers whose contents the bridge can convert. */
	public boolean hasWrappedContainer(TypeInfo target) {
		if (target.asClass().isArray()) {
			return hasAnyWrapper(target.componentType());
		}
		if (Map.class.isAssignableFrom(target.asClass())) {
			return hasAnyWrapper(target.param(0)) || hasAnyWrapper(target.param(1));
		}
		return (List.class.isAssignableFrom(target.asClass()) || Set.class.isAssignableFrom(target.asClass())
			|| target.asClass() == Collection.class) && hasAnyWrapper(target.param(0));
	}

	public boolean hasWrapper(Object from, TypeInfo target) {
		if (target instanceof TypeWrapperFactory<?>) {
			return true;
		}

		var wrapper = wrappers.get(target.asClass());
		return wrapper != null && wrapper.validator().test(from, target);
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

	@Nullable
	public TypeWrapperFactory<?> getWrapperFactory(@Nullable Object from, TypeInfo target) {
		if (target == TypeInfo.OBJECT) {
			return null;
		}

		var cl = target.asClass();

		var wrapper = wrappers.get(cl);

		if (wrapper != null && wrapper.validator().test(from, target)) {
			return wrapper.factory();
		} else {
			return null;
		}
	}

	/** Convert an explicit Groovy cast through the registered wrapper. */
	public Object coerce(Object value, TypeInfo target) {
		var cx = Context.getCurrent();
		if (target == null) {
			throw new GroovyCastException(value, Object.class);
		}

		if (target.asClass().isArray()) {
			return coerceCollection(value, target);
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
			if (!entry.validator.test(value, type)) {
				throw new IllegalArgumentException("value is not valid for " + target);
			}
			Object result = entry.factory == null ? null : entry.factory.wrap(value, type);
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

	public Object coerceCollection(Object from, TypeInfo target){
		var cx = Context.getCurrent();
		if (from instanceof Object[] arr && Collection.class.isAssignableFrom(target.asClass())) {
			return toCollection(Arrays.asList(arr), target.asClass());
		}
		if (from instanceof Iterable<?> it && Collection.class.isAssignableFrom(target.asClass())) {
			var out = new ArrayList<>();
			for (var e : it) {
				out.add(e);
			}
			return toCollection(out, target.asClass());
		}
		if (target.asClass().isArray() && from instanceof Collection<?> coll) {
			var comp = target.componentType().asClass();
			var arr = Array.newInstance(comp, coll.size());
			int i = 0;
			for (var e : coll) {
				Array.set(arr, i++, coerce(e, target.componentType()));
			}
			return arr;
		}
		return from;
	}

	private static Object toCollection(List<?> list, Class<?> target) {
		if (target.isInstance(list)) {
			return list;
		}
		if (target == Set.class || target == HashSet.class) {
			return new HashSet<>(list);
		}
		if (target == SortedSet.class || target == TreeSet.class) {
			return new TreeSet<>((Collection) list);
		}
		if (target == Queue.class || target == Deque.class || target == ArrayDeque.class) {
			return new ArrayDeque<>(list);
		}
		return list;
	}

	/** Convert an explicit Groovy cast through the registered wrapper. */
	public Object coerce(Object value, Class<?> target) {
		var cx = Context.getCurrent();
		if (target == null) {
			throw new GroovyCastException(value, Object.class);
		}

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
			Object result = entry.factory == null ? null : entry.factory.wrap(value, type);
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
