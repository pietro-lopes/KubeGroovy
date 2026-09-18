//package dev.uncandango.kubegroovy.script.bridge;
//
//import java.util.ArrayList;
//import java.util.List;
//
///**
// * Minimal legacy-compat replacement for Rhino's TypeInfo.
// * Wraps a raw Class plus optional type params. Used for script-to-Java coercion metadata.
// * Full generic fidelity is intentionally simplified for the Groovy port.
// */
//public class TypeInfo {
//	public static final TypeInfo NONE = new TypeInfo(Object.class);
//	public static final TypeInfo[] EMPTY_ARRAY = new TypeInfo[0];
//	public static final TypeInfo STRING = new TypeInfo(String.class);
//	public static final TypeInfo CHARACTER = new TypeInfo(Character.class);
//	public static final TypeInfo INT = new TypeInfo(Integer.class);
//	public static final TypeInfo PRIMITIVE_INT = new TypeInfo(Integer.class);
//	public static final TypeInfo LONG = new TypeInfo(Long.class);
//	public static final TypeInfo FLOAT = new TypeInfo(Float.class);
//	public static final TypeInfo DOUBLE = new TypeInfo(Double.class);
//	public static final TypeInfo NUMBER = new TypeInfo(Number.class);
//	public static final TypeInfo BOOLEAN = new TypeInfo(Boolean.class);
//	public static final TypeInfo RAW_MAP = new TypeInfo(java.util.Map.class);
//	public static final TypeInfo RAW_LIST = new TypeInfo(java.util.List.class);
//	public static final TypeInfo RAW_PREDICATE = new TypeInfo(java.util.function.Predicate.class);
//
//	protected final Class<?> raw;
//	protected final List<TypeInfo> params;
//
//	public TypeInfo(Class<?> raw) {
//		this.raw = raw;
//		this.params = List.of();
//	}
//
//	public TypeInfo(Class<?> raw, List<TypeInfo> params) {
//		this.raw = raw;
//		this.params = List.copyOf(params);
//	}
//
//	public static TypeInfo of(Class<?> c) {
//		if (c == null) {
//			return NONE;
//		}
//		// Return more specific subclasses where useful for instanceof checks
//		if (c.isEnum()) {
//			return new EnumTypeInfo(c);
//		}
//		if (c.isRecord()) {
//			return new RecordTypeInfo(c);
//		}
//		return new TypeInfo(c);
//	}
//
//	public static TypeInfo of(java.lang.reflect.Type t) {
//		if (t instanceof Class<?> c) {
//			return of(c);
//		}
//		if (t instanceof java.lang.reflect.ParameterizedType pt && pt.getRawType() instanceof Class<?> raw) {
//			return of(raw).withParams(ofArray(pt.getActualTypeArguments()));
//		}
//		if (t instanceof java.lang.reflect.WildcardType wildcard) {
//			var lowerBounds = wildcard.getLowerBounds();
//			var upperBounds = wildcard.getUpperBounds();
//			if (upperBounds.length == 1) {
//				return of(upperBounds[0]);
//			} else if (lowerBounds.length == 1) {
//				return of(lowerBounds[0]);
//			}
//		}
//		return NONE;
//	}
//
//	public Class<?> asClass() {
//		return raw;
//	}
//
//	public TypeInfo param(int i) {
//		if (i >= 0 && i < params.size()) {
//			return params.get(i);
//		}
//		return NONE;
//	}
//
//	public static TypeInfo[] ofArray(java.lang.reflect.Type... array) {
//		if (array.length == 0) {
//			return TypeInfo.EMPTY_ARRAY;
//		}
//		var arr = new TypeInfo[array.length];
//		for (int i = 0; i < array.length; i++) {
//			arr[i] = of(array[i]);
//		}
//		return arr;
//	}
//
//	public TypeInfo withParams(TypeInfo... ps) {
//		return new TypeInfo(raw, List.of(ps));
//	}
//
//	public TypeInfo or(TypeInfo other) {
//		// Simplified union: keep this, real coercion tries both at convert time
//		return new UnionTypeInfo(List.of(this, other));
//	}
//
//	public boolean shouldConvert() {
//		return true;
//	}
//
//	@Override
//	public String toString() {
//		if (params.isEmpty()) {
//			return raw.getSimpleName();
//		}
//		var sb = new StringBuilder(raw.getSimpleName()).append('<');
//		for (int i = 0; i < params.size(); i++) {
//			if (i > 0) {
//				sb.append(", ");
//			}
//			sb.append(params.get(i));
//		}
//		return sb.append('>').toString();
//	}
//
//	private static final class UnionTypeInfo extends TypeInfo {
//		final List<TypeInfo> options;
//
//		UnionTypeInfo(List<TypeInfo> options) {
//			super(Object.class);
//			this.options = List.copyOf(options);
//		}
//
//		@Override
//		public String toString() {
//			var sb = new StringBuilder();
//			for (int i = 0; i < options.size(); i++) {
//				if (i > 0) {
//					sb.append(" | ");
//				}
//				sb.append(options.get(i));
//			}
//			return sb.toString();
//		}
//	}
//}
