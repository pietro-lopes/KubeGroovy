package dev.uncandango.kubegroovy.script.bridge;

import dev.uncandango.kubegroovy.error.MissingContextException;
import dev.uncandango.kubegroovy.script.WithScriptContext;
import dev.uncandango.kubegroovy.script.type.ArrayTypeInfo;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import com.google.gson.JsonObject;
import groovy.lang.Closure;
import net.minecraft.nbt.CompoundTag;
import org.codehaus.groovy.runtime.ConversionHandler;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Legacy-compat context base. The real engine is KubeGroovyContext which extends this.
 * Provides minimal Rhino-like conversion helpers backed by plain Java/Groovy coercion.
 */
public class Context {
	public static final StackWalker STACK_WALKER = StackWalker.getInstance();

	public static final int CONVERSION_TRIVIAL = 1;

	public static final ScopedValue<Context> CURRENT = ScopedValue.newInstance();

	public final ContextFactory factory;

	public Context(ContextFactory factory) {
		this.factory = factory;
	}

	@Nullable
	public static Context from(Object obj) {
		var result = switch (obj) {
			case Closure<?> cl -> from(cl.getDelegate());
			case ConversionHandler ch -> from(ch.getDelegate());
			case WithScriptContext script -> script.cx();
			default -> Context.getCurrent();
		};
		if (result == null) {
			throw new MissingContextException();
		}
		return result;
	}

	@Nullable
	public static Context getCurrent() {
		return CURRENT.isBound() ? CURRENT.get() : null;
	}

	public static RuntimeException reportRuntimeError(String message, Context cx) {
		return new RhinoException(message);
	}

	public static RuntimeException reportRuntimeError(String message) {
		return new RhinoException(message);
	}

	public static RuntimeException throwAsScriptRuntimeEx(Throwable ex, Context cx) {
		if (ex instanceof RuntimeException re) {
			return re;
		}
		return new WrappedException(ex);
	}

	public static String getSourcePositionFromStack(Context cx, int[] linep) {
		if (linep != null && linep.length > 0) {
			linep[0] = -1;
		}
		// Best-effort: find first script frame
		Optional<String> fileName = STACK_WALKER.walk(stream -> stream
			.filter(frame -> Objects.nonNull(frame.getFileName()))
			.filter(frame -> frame.getFileName().endsWith(".groovy") || frame.getFileName().endsWith(".gy"))
			.findFirst().map(frame -> {
				if (linep != null && linep.length > 0) {
					linep[0] = frame.getLineNumber();
				}
				return frame.getFileName();
			}));

//		for (var el : Thread.currentThread().getStackTrace()) {
//			if (el.getFileName() != null && el.getFileName().endsWith(".groovy")) {
//				if (linep != null && linep.length > 0) {
//					linep[0] = el.getLineNumber();
//				}
//				return el.getFileName();
//			}
//		}
		return fileName.orElse("groovy");
	}

	public Object gyToJava(Object from, TypeInfo target) {
		if (target == null || !target.shouldConvert()) {
			return Wrapper.unwrapped(from);
		}

		Object unwrappedValue = Wrapper.unwrapped(from);

		if (target.asClass().isInterface()){
			if (target.asClass().isAssignableFrom(unwrappedValue.getClass())) {
				return unwrappedValue;
			}
		}

		var typeWrapper = factory.getTypeWrappers().getWrapperFactory(unwrappedValue, target);

		if (typeWrapper != null) {
			return typeWrapper.wrap(this, unwrappedValue, target);
		}

		if (target.is(TypeInfo.RAW_SET)) {
			return setOf(from, target.param(0));
		} else if (target.is(TypeInfo.RAW_MAP)) {
			return mapOf(from, target.param(0), target.param(1));
		} else if (target instanceof ArrayTypeInfo) {
			return arrayOf(from, target.componentType());
		} else if (List.class.isAssignableFrom(target.asClass())) {
			return listOf(from, target.param(0));
		} else if (target.is(TypeInfo.CLASS)) {
			return classOf(from);
		}

		if (from == null || from.getClass() == target.asClass()) {
			return from;
		}

		return internalGyToJava(from, target);
	}

	protected Object internalGyToJava(Object from, TypeInfo target) {
		if (target instanceof ArrayTypeInfo) {
			var arrayType = target.componentType();
			{
				// Convert a single value to an array
				Object result = arrayType.newArray(1);
				Array.set(result, 0, gyToJava(from, arrayType));
				return result;
			}
		}

//		Object unwrappedValue = Wrapper.unwrapped(from);
//
//		if (target.asClass().isInterface()){
//			if (target.asClass().isAssignableFrom(unwrappedValue.getClass())) {
//				return unwrappedValue;
//			}
//		}
//
//		var typeWrapper = factory.getTypeWrappers().getWrapperFactory(unwrappedValue, target);
//
//		if (typeWrapper != null) {
//			return typeWrapper.wrap(this, unwrappedValue, target);
//		}

		//return GroovyConversions.toJava(from, target == null ? Object.class : target.asClass());
		return internalGyToJavaLast(from, target);
	}

	public Object javaToGY(Object value, Scriptable scope) {
		return GroovyConversions.toScript(value);
	}

	public Object javaToGY(Object value, Scriptable scope, TypeInfo target) {
		return GroovyConversions.toScript(value);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public <T> T createInterfaceAdapter(TypeInfo type, Object fn) {
		Class<?> raw = type == null ? Object.class : type.asClass();
		if (raw.isInterface() && (fn instanceof Closure || fn instanceof BaseFunction)) {
			return (T) GroovyConversions.toFunctionalInterface(fn, raw);
		}
		return (T) DefaultGroovyMethods.asType(fn, (Class) raw);
	}

	public boolean isListLike(Object o) {
		return o instanceof List || o instanceof Object[] || o instanceof Iterable;
	}

	public boolean isMapLike(Object o) {
		return o instanceof Map || o instanceof CompoundTag || o instanceof JsonObject;
	}

	public boolean canConvert(Object from, TypeInfo target) {
		if (from == null || target == null || target == TypeInfo.NONE) {
			return true;
		}
		try {
			var c = target.asClass();
			if (c == Object.class || c.isInstance(Wrapper.unwrapped(from))) {
				return true;
			}
			// Best-effort: allow strings/numbers/maps/lists to convert
			return from instanceof CharSequence || from instanceof Number || from instanceof Boolean
				|| from instanceof Map || from instanceof List || from instanceof Iterable;
		} catch (Exception e) {
			return false;
		}
	}

	@SuppressWarnings("unchecked")
	public <K, V> Map<K, V> optionalMapOf(Object from) {
		return (Map<K, V>) optionalMapOf(from, TypeInfo.NONE, TypeInfo.NONE);
	}

	@SuppressWarnings("unchecked")
	public <K, V> Map<K, V> optionalMapOf(Object from, TypeInfo k, TypeInfo v) {
		if (from == null) {
			return null;
		}
		if (from instanceof Map<?, ?> m) {
			return (Map<K, V>) m;
		}
		if (from instanceof CompoundTag tag) {
			var map = new LinkedHashMap<String, Object>();
			for (var key : tag.keySet()) {
				map.put(key, tag.get(key));
			}
			return (Map<K, V>) map;
		}
		if (from instanceof JsonObject json) {
			var map = new LinkedHashMap<String, Object>();
			for (var e : json.entrySet()) {
				map.put(e.getKey(), e.getValue());
			}
			return (Map<K, V>) map;
		}
		return null;
	}

	public Object listOf(@Nullable Object from, TypeInfo target) {
		return arrayValueProviderOf(from).createList(this, target);
		//		if (from instanceof Object[] arr) {
//			if (target == null) {
//				return from;
//			}
//
//			if (arr.length == 0) {
//				return new ArrayList<>(0);
//			} else {
//				int len = arr.length;
//				var list = new ArrayList<>(0);
//
//				for (Object o : arr) {
//					try {
//						list.add(this.gyToJava(o, target));
//					} catch (EvaluatorException ee) {
//						return this.reportConversionError(arr, target);
//					}
//				}
//
//				return list;
//			}
//
//		} else if (from != null && from.getClass().isArray()) {
//			if (target == null) {
//				return from;
//			}
//
//			int len = Array.getLength(from);
//			if (len == 0) {
//				return new ArrayList<>(0);
//			}
//
//			var list = new ArrayList<>();
//			for (int i = 0; i < len; i++) {
//				try {
//					list.add(this.gyToJava(Array.get(from, i), target));
//				} catch (EvaluatorException ee) {
//					return this.reportConversionError(from, target);
//				}
//			}
//
//			return list;
//		}
//
//		if (from instanceof Iterable<?> iterable) {
//			var list = new ArrayList<>();
//			iterable.forEach(element -> {
//				list.add(this.gyToJava(element, target));
//			});
//
//			return list;
//		}
//
//		if (from == null) return from;
//
//		return this.reportConversionError(from, target);
	}

	@SuppressWarnings("unchecked")
	public <T> List<T> optionalListOf(Object from, TypeInfo element) {
		if (from == null) {
			return null;
		}
		Object unwrapped = Wrapper.unwrapped(from);
		if (unwrapped instanceof List<?> l) {
			return (List<T>) l;
		}
		if (unwrapped instanceof Object[] arr) {
			return (List<T>) List.of(arr);
		}
		if (unwrapped instanceof Iterable<?> it) {
			var out = new java.util.ArrayList<T>();
			for (var e : it) {
				out.add((T) gyToJava(e, element.componentType()));
			}
			return out;
		}
		return null;
	}

	public Object mapOf(Object from, TypeInfo kTarget, TypeInfo vTarget) {
		var m = optionalMapOf(from, kTarget, vTarget);
		if (m == null) {
			return reportConversionError(from, TypeInfo.RAW_MAP);
		}
		return m;
	}

	public Object evaluateString(Scriptable scope, String source, String location, int line, Object security) {
		// Overridden by KubeGroovyContext with real GroovyShell. Base is noop.
		return null;
	}

	public void addToScope(Scriptable scope, String name, Object value) {
		if (scope instanceof ScriptableObject so) {
			so.put(name, value);
		} else if (scope instanceof Map<?, ?>) {
			@SuppressWarnings("unchecked")
			var m = (Map<String, Object>) scope;
			m.put(name, value);
		}
	}

	public Scriptable initStandardObjects() {
		return new ScriptableObject();
	}

	public void setApplicationClassLoader(ClassLoader cl) {
	}

	public Scriptable wrapAsJavaObject(Scriptable scope, Object javaObject, TypeInfo target) {
		if (javaObject instanceof Scriptable s) {
			return s;
		}
		return new NativeJavaObject(javaObject);
	}

	public int internalConversionWeightLast(Object fromObj, TypeInfo target) {
		return 0;
	}

	protected Object internalGyToJavaLast(Object from, TypeInfo target) {
		if (target instanceof TypeWrapperFactory<?> f) {
			return f.wrap(this, from, target);
		}

		return reportConversionError(from, target);
		//return GroovyConversions.toJava(from, target == null ? Object.class : target.asClass());
	}

	public ArrayValueProvider arrayValueProviderOf(Object value) {
		if (value instanceof Object[] arr) {
			return arr.length == 0 ? ArrayValueProvider.EMPTY : new ArrayValueProvider.FromPlainJavaArray(arr);
		} else if (value != null && value.getClass().isArray()) {
			int len = Array.getLength(value);
			return len == 0 ? ArrayValueProvider.EMPTY : new ArrayValueProvider.FromJavaArray(value, len);
		}

		return switch (value) {
			case Wrapper w -> arrayValueProviderOf(w.unwrap()); // other stuff like NJO wrapping set falls through to this
			case List<?> list -> ArrayValueProvider.fromJavaList(list, list);
			case Iterable<?> itr -> ArrayValueProvider.fromIterable(itr);
			case null, default -> value == null ? ArrayValueProvider.FromObject.FROM_NULL : new ArrayValueProvider.FromObject(value);
		};
	}

	public Object classOf(Object from) {
		if (from instanceof Class<?> c) {
			return c;
		}
		try {
			return Class.forName(String.valueOf(from));
		} catch (Exception e) {
			throw reportRuntimeError("Class not found: " + from, this);
		}
	}

	public Object setOf(@Nullable Object from, TypeInfo target) {
		return arrayValueProviderOf(from).createSet(this, target);
//		if (from instanceof Set<?> set) {
//			return set;
//		}
//
//		if (from instanceof List<?> list) {
//			var set = new LinkedHashSet<>(list.size());
//
//			for (var o : list) {
//				set.add(gyToJava(o, target));
//			}
//
//			return set;
//		}
//
//		if (from instanceof Object[] objArray) {
//			var set = new LinkedHashSet<>(objArray.length);
//
//			for (var o : objArray) {
//				set.add(gyToJava(o, target));
//			}
//
//			return set;
//		}
//
//		if (from instanceof Collection<?> collection) {
//			var set = new LinkedHashSet<>(collection.size());
//
//			for (var o : collection) {
//				set.add(gyToJava(o, target));
//			}
//
//			return set;
//		}
//		if (from == null) return from;
//		return this.reportConversionError(from, target);
	}

	public Object arrayOf(@Nullable Object from, TypeInfo target) {
		return arrayValueProviderOf(from).createArray(this, target);
//		if (from instanceof Object[] arr) {
//			if (target == null) {
//				return from;
//			}
//
//			if (arr.length == 0) {
//				return target.newArray(0);
//			} else {
//				int len = arr.length;
//				var array = target.newArray(len);
//
//				for (int i = 0; i < len; i++) {
//					try {
//						Array.set(array, i, this.gyToJava(arr[i], target));
//					} catch (EvaluatorException ee) {
//						return this.reportConversionError(arr, target);
//					}
//				}
//
//				return array;
//			}
//
//		} else if (from != null && from.getClass().isArray()) {
//			if (target == null) {
//				return from;
//			}
//
//			int len = Array.getLength(from);
//			if (len == 0) {
//				return target.newArray(0);
//			}
//
//			var array = target.newArray(len);
//			for (int i = 0; i < len; i++) {
//				try {
//					Array.set(array, i, this.gyToJava(Array.get(from, i), target));
//				} catch (EvaluatorException ee) {
//					return this.reportConversionError(from, target);
//				}
//			}
//
//			return array;
//		}
//
//		if (from instanceof Iterable<?> iterable) {
//			var list = new ArrayList<>();
//			iterable.forEach(list::add);
//			var len = list.size();
//			var array = target.newArray(len);
//			if (len == 0) {
//				return array;
//			}
//			for (int i = 0; i < len; i++) {
//				try {
//					Array.set(array, i, this.gyToJava(list.get(i), target));
//				} catch (EvaluatorException ee) {
//					return this.reportConversionError(iterable, target);
//				}
//			}
//			return array;
//		}
//
//		if (from == null) return from;
//		return this.reportConversionError(from, target);
	}

	public Object toString(Object o) {
		return String.valueOf(o);
	}

	public Object reportConversionError(Object value, TypeInfo type) {
		throw Context.reportRuntimeError("Cannot convert %s to %s".formatted(String.valueOf(value), type.signature()), this);
	}
}
