package dev.uncandango.kubegroovy.script.bridge;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/** Legacy-compat util for type conversions. Backed by GroovyConversions in real engine. */
public final class TypeUtils {
	private TypeUtils() {
	}

	public static Class<?> getRawType(Type type) {
		if (type instanceof Class<?> c) {
			return c;
		}
		if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> raw) {
			return raw;
		}
		if (type instanceof GenericArrayType gat) {
			var comp = getRawType(gat.getGenericComponentType());
			return Array.newInstance(comp, 0).getClass();
		}
		return Object.class;
	}
}
