package dev.uncandango.kubegroovy.script.bridge;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy-compat context base. The real engine is KubeGroovyContext which extends this.
 * Provides minimal Rhino-like conversion helpers backed by plain Java/Groovy coercion.
 */
public class Context {
	public static final int CONVERSION_TRIVIAL = 1;

	private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

	public static Context getCurrent() {
		return CURRENT.get();
	}

	public static void setCurrent(Context cx) {
		if (cx == null) {
			CURRENT.remove();
		} else {
			CURRENT.set(cx);
		}
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
		for (var el : Thread.currentThread().getStackTrace()) {
			if (el.getFileName() != null && el.getFileName().endsWith(".groovy")) {
				if (linep != null && linep.length > 0) {
					linep[0] = el.getLineNumber();
				}
				return el.getFileName();
			}
		}
		return "groovy";
	}

	public Object gyToJava(Object from, TypeInfo target) {
		return GroovyConversions.toJava(from, target == null ? Object.class : target.asClass());
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
		return (T) GroovyConversions.toFunctionalInterface(fn, (Class) raw);
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
				out.add((T) e);
			}
			return out;
		}
		return null;
	}

	public Object mapOf(Object from, TypeInfo kTarget, TypeInfo vTarget) {
		var m = optionalMapOf(from, kTarget, vTarget);
		return m == null ? Map.of() : m;
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
		return GroovyConversions.toJava(from, target == null ? Object.class : target.asClass());
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

	public Object toString(Object o) {
		return String.valueOf(o);
	}
}
