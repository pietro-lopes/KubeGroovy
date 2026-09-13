package dev.uncandango.kubegroovy.script.bridge;

import java.util.HashMap;
import java.util.Map;

/** Legacy-compat map wrapper. */
public class NativeJavaMap extends HashMap<String, Object> implements Scriptable, Wrapper {
	protected final Map<?, ?> map;
	protected final Object javaObject;
	protected final TypeInfo type;

	public NativeJavaMap(Context cx, Scriptable scope, Object javaObject, Map<?, ?> map, TypeInfo type) {
		super();
		this.javaObject = javaObject;
		this.map = map == null ? Map.of() : map;
		this.type = type;
		if (map != null) {
			for (var e : map.entrySet()) {
				super.put(String.valueOf(e.getKey()), e.getValue());
			}
		}
	}

	public NativeJavaMap(Context cx, Scriptable scope, Map<?, ?> map) {
		this(cx, scope, map, map, TypeInfo.RAW_MAP);
	}

	public NativeJavaMap(Map<?, ?> map) {
		this(null, null, map, map, TypeInfo.RAW_MAP);
	}

	@Override
	public Object unwrap() {
		return javaObject != null ? javaObject : map;
	}

	public Map<?, ?> getMap() {
		return map;
	}

	public Object get(Context cx, String name, Scriptable start) {
		return super.get(name);
	}

	public Object get(Context cx, int index, Scriptable start) {
		return super.get(String.valueOf(index));
	}
}
