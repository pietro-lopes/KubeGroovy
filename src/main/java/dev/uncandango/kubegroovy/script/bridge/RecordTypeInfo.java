package dev.uncandango.kubegroovy.script.bridge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RecordTypeInfo extends TypeInfo {
	private static final Map<Class<?>, Map<String, Object>> GLOBAL_DEFAULTS = new ConcurrentHashMap<>();

	public RecordTypeInfo(Class<?> raw) {
		super(raw);
	}

	public static void setGlobalDefaultValue(Class<?> type, Object def) {
		GLOBAL_DEFAULTS.computeIfAbsent(type, k -> new ConcurrentHashMap<>()).put("__default__", def);
	}

	public static Map<String, Object> getDefaults(Class<?> type) {
		return GLOBAL_DEFAULTS.getOrDefault(type, Map.of());
	}

	/** Minimal legacy-compat: coerce via GroovyConversions. */
	public Object wrap(Context cx, Object from, TypeInfo target) {
		return GroovyConversions.toJava(from, asClass());
	}

	public TypeInfo createCombinedType(TypeInfo... parts) {
		// Simplified union of record + others
		TypeInfo acc = this;
		for (var p : parts) {
			acc = acc.or(p);
		}
		return acc;
	}
}
