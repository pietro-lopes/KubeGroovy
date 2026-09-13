package dev.uncandango.kubegroovy.script.bridge;

/** Legacy-compat util for type conversions. Backed by GroovyConversions in real engine. */
public final class TypeUtils {
	private TypeUtils() {
	}

	public static Class<?> getRawType(java.lang.reflect.Type type) {
		if (type instanceof Class<?> c) {
			return c;
		}
		if (type instanceof java.lang.reflect.ParameterizedType pt && pt.getRawType() instanceof Class<?> raw) {
			return raw;
		}
		if (type instanceof java.lang.reflect.GenericArrayType gat) {
			var comp = getRawType(gat.getGenericComponentType());
			return java.lang.reflect.Array.newInstance(comp, 0).getClass();
		}
		return Object.class;
	}
}
