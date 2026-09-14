package dev.uncandango.kubegroovy.script.groovy;

import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.BaseFunction;
import dev.uncandango.kubegroovy.script.bridge.GroovyConversions;
import groovy.lang.Closure;
import groovy.lang.MetaClassImpl;
import groovy.lang.MetaClassRegistry;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Normal Groovy dispatch first, followed by a deliberately small compatibility
 * fallback for KubeGroovy's annotated addon interfaces and context-leading APIs.
 */
final class KubeGroovyMetaClass extends MetaClassImpl {
	KubeGroovyMetaClass(MetaClassRegistry registry, Class<?> type) {
		super(registry, type);
	}

	@Override
	public Object invokeMethod(Object object, String name, Object[] arguments) {
		try {
			return super.invokeMethod(object, name, arguments);
		} catch (RuntimeException missing) {
			MissingMethodException dispatchFailure = missingMethodCause(missing);
			if (dispatchFailure == null) {
				throw missing;
			}
			Object result = invokeFallback(object, name, arguments == null ? new Object[0] : arguments);
			if (result != Fallback.MISSING) {
				return result;
			}
			throw dispatchFailure;
		}
	}

	@Override
	public Object invokeMethod(Class sender, Object object, String name, Object[] arguments, boolean isCallToSuper, boolean fromInsideClass) {
		Object[] actualArguments = arguments == null ? new Object[0] : arguments;
		Object contextResult = invokeContextFallback(object, name, actualArguments);
		if (contextResult != Fallback.MISSING) {
			return contextResult;
		}
		try {
			return super.invokeMethod(sender, object, name, actualArguments, isCallToSuper, fromInsideClass);
		} catch (RuntimeException missing) {
			MissingMethodException dispatchFailure = missingMethodCause(missing);
			if (dispatchFailure == null) {
				throw missing;
			}
			Object result = invokeFallback(object, name, arguments == null ? new Object[0] : arguments);
			if (result != Fallback.MISSING) {
				return result;
			}
			throw dispatchFailure;
		}
	}

	@Override
	public Object invokeStaticMethod(Object object, String name, Object[] arguments) {
		try {
			return super.invokeStaticMethod(object, name, arguments);
		} catch (RuntimeException missing) {
			MissingMethodException dispatchFailure = missingMethodCause(missing);
			if (dispatchFailure == null) {
				throw missing;
			}
			Class<?> receiver = object instanceof Class<?> c ? c : getTheClass();
			Object result = invokeFallback(receiver, name, arguments == null ? new Object[0] : arguments);
			if (result != Fallback.MISSING) {
				return result;
			}
			throw dispatchFailure;
		}
	}

	private static MissingMethodException missingMethodCause(RuntimeException error) {
		Throwable current = error;
		while (current != null) {
			if (current instanceof MissingMethodException missing) {
				return missing;
			}
			if (current == current.getCause()) {
				break;
			}
			current = current.getCause();
		}
		return null;
	}

	@Override
	public Object getProperty(Class sender, Object object, String property, boolean useSuper, boolean fromInsideClass) {
		try {
			Object result = super.getProperty(sender, object, property, useSuper, fromInsideClass);
			KubeGroovyMetaClassCreationHandle.refreshIfNeeded(result);
			return result;
		} catch (MissingPropertyException missing) {
			Object result = invokeFallback(object, property, new Object[0]);
			if (result != Fallback.MISSING) {
				KubeGroovyMetaClassCreationHandle.refreshIfNeeded(result);
				return result;
			}
			throw missing;
		}
	}

	@Override
	public Object getProperty(Object object, String property) {
		return getProperty(object == null ? null : object.getClass(), object, property, false, false);
	}

	@Override
	public void setProperty(Class sender, Object object, String property, Object newValue, boolean useSuper, boolean fromInsideClass) {
		try {
			super.setProperty(sender, object, property, newValue, useSuper, fromInsideClass);
		} catch (MissingPropertyException missing) {
			Object result = invokeFallback(object, property, new Object[]{newValue});
			if (result != Fallback.MISSING) {
				return;
			}
			throw missing;
		}
	}

	@Override
	public void setProperty(Object object, String property, Object newValue) {
		setProperty(object == null ? null : object.getClass(), object, property, newValue, false, false);
	}

	private static Object invokeFallback(Object receiver, String requested, Object[] args) {
		if (receiver == null) {
			return Fallback.MISSING;
		}
		Class<?> type = receiver instanceof Class<?> c ? c : receiver.getClass();
		for (Method method : methodsOf(type)) {
			if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic() || method.isBridge()
				|| receiver instanceof Class<?> && !Modifier.isStatic(method.getModifiers()) || hidden(method)) {
				continue;
			}
			if (!matches(method, requested, args.length)) {
				continue;
			}
			Object[] actual = adaptArguments(method, args);
			if (actual == null) {
				continue;
			}
			try {
				return method.invoke(receiver instanceof Class<?> ? null : receiver, actual);
			} catch (InvocationTargetException ex) {
				Throwable cause = ex.getCause();
				if (cause instanceof RuntimeException runtime) {
					throw runtime;
				}
				if (cause instanceof Error error) {
					throw error;
				}
				throw new RuntimeException(cause);
			} catch (ReflectiveOperationException ignored) {
				// Continue to the next overload.
			}
		}

		if (args.length == 0 || args.length == 1) {
			String cap = requested.isEmpty() ? requested : Character.toUpperCase(requested.charAt(0)) + requested.substring(1);
			for (Method method : methodsOf(type)) {
				if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic() || method.isBridge()
					|| receiver instanceof Class<?> && !Modifier.isStatic(method.getModifiers()) || hidden(method)) {
					continue;
				}
				String getter = args.length == 0 ? "get" + cap : "set" + cap;
				if (!matches(method, getter, args.length) && !matches(method, getter, args.length, true)) {
					continue;
				}
				Object[] actual = adaptArguments(method, args);
				if (actual == null) {
					continue;
				}
				try {
					return method.invoke(receiver instanceof Class<?> ? null : receiver, actual);
				} catch (InvocationTargetException ex) {
					Throwable cause = ex.getCause();
					if (cause instanceof RuntimeException runtime) throw runtime;
					if (cause instanceof Error error) throw error;
					throw new RuntimeException(cause);
				} catch (ReflectiveOperationException ignored) {
				}
			}
		}

		if (args.length == 0) {
			for (Field field : type.getFields()) {
				if (hidden(field) || !Modifier.isPublic(field.getModifiers())) continue;
				var remap = field.getAnnotation(RemapForGY.class);
				if (remap != null && (remap.value().equals(requested) || remap.value().equalsIgnoreCase(requested))) {
					try {
						return field.get(receiver instanceof Class<?> ? null : receiver);
					} catch (ReflectiveOperationException ignored) {
					}
				}
			}
		}
		return Fallback.MISSING;
	}

	/**
	 * Invokes only context-leading methods before Groovy's six-argument call-site
	 * dispatch.  Groovy resolves these call sites directly and can otherwise
	 * attempt to load the sandboxed java.lang.Class type before methodMissing is
	 * reached.  Ordinary methods still take the normal Groovy path first.
	 */
	private static Object invokeContextFallback(Object receiver, String requested, Object[] args) {
		if (receiver == null) {
			return Fallback.MISSING;
		}
		Class<?> type = receiver instanceof Class<?> c ? c : receiver.getClass();
		for (Method method : methodsOf(type)) {
			if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic() || method.isBridge()
				|| receiver instanceof Class<?> && !Modifier.isStatic(method.getModifiers()) || hidden(method)
				|| !hasContext(method, args.length) || !matches(method, requested, args.length)) {
				continue;
			}
			if (hasNormalCandidate(type, requested, args.length)) {
				return Fallback.MISSING;
			}
			Object[] actual = adaptArguments(method, args);
			if (actual == null) {
				continue;
			}
			try {
				return method.invoke(receiver instanceof Class<?> ? null : receiver, actual);
			} catch (InvocationTargetException ex) {
				Throwable cause = ex.getCause();
				if (cause instanceof RuntimeException runtime) throw runtime;
				if (cause instanceof Error error) throw error;
				throw new RuntimeException(cause);
			} catch (ReflectiveOperationException ignored) {
				// Try another overload.
			}
		}
		return Fallback.MISSING;
	}

	/**
	 * Mixin interface injection can leave default methods visible through the
	 * interface but absent from the transformed concrete class's reflective
	 * method array. Include those declarations explicitly for bridge lookup.
	 */
	private static List<Method> methodsOf(Class<?> type) {
		var methods = new ArrayList<Method>();
		var seen = new java.util.HashSet<String>();
		for (Method method : type.getMethods()) {
			if (seen.add(method.toGenericString())) {
				methods.add(method);
			}
		}
		collectInterfaceMethods(type, methods, seen);
		return methods;
	}

	private static void collectInterfaceMethods(Class<?> type, List<Method> methods, java.util.Set<String> seen) {
		for (Class<?> iface : type.getInterfaces()) {
			for (Method method : iface.getDeclaredMethods()) {
				if (seen.add(method.toGenericString())) {
					methods.add(method);
				}
			}
			collectInterfaceMethods(iface, methods, seen);
		}
		if (type.getSuperclass() != null && type.getSuperclass() != Object.class) {
			collectInterfaceMethods(type.getSuperclass(), methods, seen);
		}
	}

	private static boolean hasNormalCandidate(Class<?> type, String requested, int argumentCount) {
		for (Method candidate : type.getMethods()) {
			if (Modifier.isPublic(candidate.getModifiers()) && !candidate.isSynthetic() && !candidate.isBridge()
				&& candidate.getName().equals(requested) && candidate.getParameterCount() == argumentCount
				&& !hasContext(candidate, argumentCount) && !hidden(candidate)) {
				return true;
			}
		}
		return false;
	}

	private static boolean matches(Method method, String requested, int argumentCount) {
		return matches(method, requested, argumentCount, false);
	}

	private static boolean matches(Method method, String requested, int argumentCount, boolean propertyAlias) {
		List<String> aliases = new ArrayList<>();
		aliases.add(method.getName());
		RemapForGY remap = method.getAnnotation(RemapForGY.class);
		if (remap != null && !remap.value().isEmpty()) aliases.add(remap.value());
		RemapPrefixForGY prefix = prefixFor(method.getDeclaringClass());
		if (prefix != null && !prefix.value().isEmpty() && method.getName().startsWith(prefix.value())) {
			aliases.add(method.getName().substring(prefix.value().length()));
		}
		for (String alias : aliases) {
			if (alias.equals(requested) || alias.equalsIgnoreCase(requested)) {
				return method.getParameterCount() == argumentCount || hasContext(method, argumentCount);
			}
		}
		return false;
	}

	private static RemapPrefixForGY prefixFor(Class<?> type) {
		RemapPrefixForGY prefix = type.getAnnotation(RemapPrefixForGY.class);
		if (prefix != null) {
			return prefix;
		}
		for (Class<?> iface : type.getInterfaces()) {
			prefix = prefixFor(iface);
			if (prefix != null) {
				return prefix;
			}
		}
		return type.getSuperclass() == null ? null : prefixFor(type.getSuperclass());
	}

	private static boolean hasContext(Method method, int argumentCount) {
		return method.getParameterCount() == argumentCount + 1 && Context.class.isAssignableFrom(method.getParameterTypes()[0]);
	}

	private static Object[] withContext(Method method, Object[] args) {
		if (hasContext(method, args.length)) {
			Context cx = GroovyConversions.getActiveContext();
			if (cx == null) {
				return null;
			}
			Object[] actual = new Object[args.length + 1];
			actual[0] = cx;
			System.arraycopy(args, 0, actual, 1, args.length);
			return actual;
		}
		return method.getParameterCount() == args.length ? args : null;
	}

	private static Object[] adaptArguments(Method method, Object[] args) {
		Object[] actual = withContext(method, args);
		if (actual == null) {
			return null;
		}
		Class<?>[] parameters = method.getParameterTypes();
		for (int i = 0; i < parameters.length; i++) {
			Object value = actual[i];
			if (value == null) {
				if (parameters[i].isPrimitive()) return null;
				continue;
			}
			if (parameters[i].isInstance(value)) {
				continue;
			}
			if (parameters[i].isPrimitive()) {
				Object converted = convertPrimitive(value, parameters[i]);
				if (converted == null) {
					return null;
				}
				actual[i] = converted;
				continue;
			}
			if (parameters[i].isEnum() && value instanceof CharSequence text) {
				try {
					@SuppressWarnings({"unchecked", "rawtypes"})
					Object converted = Enum.valueOf((Class<? extends Enum>) parameters[i], text.toString().toUpperCase(Locale.ROOT));
					actual[i] = converted;
					continue;
				} catch (IllegalArgumentException ignored) {
					return null;
				}
			}
			if (parameters[i].isInterface() && (value instanceof Closure<?> || value instanceof BaseFunction)) {
				try {
					actual[i] = GroovyConversions.toFunctionalInterface(value, parameters[i]);
				} catch (RuntimeException ignored) {
					return null;
				}
				continue;
			}
			return null;
		}
		return actual;
	}

	private static Object convertPrimitive(Object value, Class<?> type) {
		if (type == boolean.class) return value instanceof Boolean ? value : null;
		if (type == char.class) return value instanceof Character ? value : null;
		if (!(value instanceof Number number)) return null;
		if (type == byte.class) return number.byteValue();
		if (type == short.class) return number.shortValue();
		if (type == int.class) return number.intValue();
		if (type == long.class) return number.longValue();
		if (type == float.class) return number.floatValue();
		if (type == double.class) return number.doubleValue();
		return null;
	}

	private static boolean hidden(java.lang.reflect.AnnotatedElement member) {
		if (member.isAnnotationPresent(HideFromGY.class)) {
			return true;
		}
		if (member instanceof Method method) {
			return hasHideAnnotation(method.getDeclaringClass());
		}
		return false;
	}

	private static boolean hasHideAnnotation(Class<?> type) {
		if (type.isAnnotationPresent(HideFromGY.class)) {
			return true;
		}
		for (Class<?> iface : type.getInterfaces()) {
			if (hasHideAnnotation(iface)) {
				return true;
			}
		}
		return type.getSuperclass() != null && type.getSuperclass() != Object.class && hasHideAnnotation(type.getSuperclass());
	}

	private enum Fallback { MISSING }
}
