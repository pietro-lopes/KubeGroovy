package dev.uncandango.kubegroovy.script.bridge;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.script.KubeGroovyContext;
import dev.uncandango.kubegroovy.script.groovy.KubeGroovyMetaClassCreationHandle;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/** Minimal Groovy coercion helpers used by bridge Context and new engine. */
public final class GroovyConversions {

	private GroovyConversions() {
	}

	/** Context currently associated with a Groovy callback, including callbacks
	 * running on an event thread where the legacy Context thread-local is absent. */
	public static Context getActiveContext() {
		return Context.getCurrent();
	}

	public static Object toScript(Object value) {
		if (value == null) {
			return null;
		}
		return Wrapper.unwrapped(value);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Object toJava(Object from, Class<?> target) {
		if (from == null || target == null || target == Object.class) {
			return Wrapper.unwrapped(from);
		}
		from = Wrapper.unwrapped(from);

		if (from == null) {
			return null;
		}
		// Groovy interpolated strings are GString, normalize to String early
		if (from instanceof groovy.lang.GString gs) {
			from = gs.toString();
		}
		if (target.isInstance(from)) {
			return from;
		}
		// CharSequence targets accept anything via toString (covers GString, StringBuilder, etc.)
		if (target == CharSequence.class) {
			return String.valueOf(from);
		}
		try {
			if (target == String.class) {
				return String.valueOf(from);
			}
			if (target == Integer.class || target == int.class) {
				return toInt(from);
			}
			if (target == Long.class || target == long.class) {
				return toLong(from);
			}
			if (target == Double.class || target == double.class) {
				return toDouble(from);
			}
			if (target == Float.class || target == float.class) {
				return (float) toDouble(from);
			}
			if (target == Short.class || target == short.class) {
				return (short) toInt(from);
			}
			if (target == Byte.class || target == byte.class) {
				return (byte) toInt(from);
			}
			if (target == java.math.BigInteger.class && from instanceof Number n) {
				return java.math.BigInteger.valueOf(n.longValue());
			}
			if (target == java.math.BigDecimal.class && from instanceof Number n) {
				return java.math.BigDecimal.valueOf(n.doubleValue());
			}
			if (target == Boolean.class || target == boolean.class) {
				return toBoolean(from);
			}
			if ((target == Character.class || target == char.class) && from instanceof CharSequence cs && cs.length() == 1) {
				return cs.charAt(0);
			}
			if ((target == Character.class || target == char.class) && from instanceof Number n) {
				return (char) n.intValue();
			}
			if (from instanceof Object[] arr && java.util.Collection.class.isAssignableFrom(target)) {
				return toCollection(java.util.Arrays.asList(arr), target);
			}
			if (from instanceof Iterable<?> it && java.util.Collection.class.isAssignableFrom(target)) {
				var out = new java.util.ArrayList<>();
				for (var e : it) {
					out.add(e);
				}
				return toCollection(out, target);
			}
			if (target.isArray() && from instanceof java.util.Collection<?> coll) {
				var comp = target.getComponentType();
				var arr = java.lang.reflect.Array.newInstance(comp, coll.size());
				int i = 0;
				for (var e : coll) {
					java.lang.reflect.Array.set(arr, i++, toJava(e, comp));
				}
				return arr;
			}
			if (target.isEnum() && from instanceof CharSequence cs) {
				var name = cs.toString().trim();
				for (var constant : target.getEnumConstants()) {
					if (((Enum<?>) constant).name().equalsIgnoreCase(name)) {
						return constant;
					}
				}
				return Enum.valueOf((Class<Enum>) target, name.toUpperCase(java.util.Locale.ROOT));
			}
			// Functional interfaces from Closure/BaseFunction
			if (target.isInterface() && from instanceof BaseFunction fn) {
				return toFunctionalInterface(fn.getWrapped() == null ? fn : fn.getWrapped(), target);
			}
			if (target.isInterface() && from instanceof Closure<?> c) {
				return toFunctionalInterface(c, target);
			}
			if (target.isInterface() && from instanceof Map<?,?>) {
				return DefaultGroovyMethods.asType(from, target);
			}
		} catch (Exception ignored) {
		}
		return from;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object toCollection(java.util.List<?> list, Class<?> target) {
		if (target.isInstance(list)) {
			return list;
		}
		if (target == java.util.Set.class || target == java.util.HashSet.class) {
			return new java.util.HashSet<>(list);
		}
		if (target == java.util.SortedSet.class || target == java.util.TreeSet.class) {
			return new java.util.TreeSet<>((java.util.Collection) list);
		}
		if (target == java.util.Queue.class || target == java.util.Deque.class || target == java.util.ArrayDeque.class) {
			return new java.util.ArrayDeque<>(list);
		}
		return list;
	}

	public static int toInt(Object o) {
		if (o instanceof Number n) {
			return n.intValue();
		}
		return Integer.parseInt(String.valueOf(o));
	}

	public static long toLong(Object o) {
		if (o instanceof Number n) {
			return n.longValue();
		}
		return Long.parseLong(String.valueOf(o));
	}

	public static double toDouble(Object o) {
		if (o instanceof Number n) {
			return n.doubleValue();
		}
		return Double.parseDouble(String.valueOf(o));
	}

	public static boolean toBoolean(Object o) {
		return ScriptRuntime.toBoolean(o);
	}

	@SuppressWarnings("unchecked")
	public static <T> T toFunctionalInterface(Object fn, Class<T> type) {
		var cx = Context.getCurrent();
		if (fn == null) {
			return null;
		}
		if (type.isInstance(fn)) {
			return (T) fn;
		}
		Object delegate = fn instanceof BaseFunction bf ? bf.getWrapped() : fn;
		if (delegate == null) {
			delegate = fn;
		}
		if (type.isInstance(delegate)) {
			return (T) delegate;
		}
		final Object target = delegate;
		if (!type.isInterface() || type.getMethods().length == 0) {
			return (T) target;
		}
		// Single-abstract-method proxy: call Closure/callable with args
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, new InvocationHandler() {
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				if (args != null) {
					for (Object argument : args) {
						KubeGroovyMetaClassCreationHandle.refreshIfNeeded(argument);
					}
				}
				if (method.getDeclaringClass() == Object.class) {
					return ScopedValue.where(KubeGroovyContext.CURRENT, cx).call(() -> method.invoke(target, args));
				}
				if (target instanceof Closure<?> c) {
					if (args == null || args.length == 0) {
						return ScopedValue.where(KubeGroovyContext.CURRENT, cx).call(c::call);
					}
					if (args.length == 1) {
						return ScopedValue.where(KubeGroovyContext.CURRENT, cx).call(() -> c.call(args[0]));
					}
					return ScopedValue.where(KubeGroovyContext.CURRENT, cx).call(() -> c.call(args));
				}
				if (target instanceof BaseFunction bf) {
					return bf.call(cx, null, null, args == null ? new Object[0] : args);
				}
				if (target instanceof java.util.function.Function<?, ?> f) {
					@SuppressWarnings("rawtypes")
					var func = (java.util.function.Function) f;
					return ScopedValue.where(KubeGroovyContext.CURRENT, cx).call(() -> func.apply(args == null || args.length == 0 ? null : args[0]));
				}
				if (target instanceof groovy.lang.GroovyCallable<?> gc) {
					try {
						ScopedValue.where(KubeGroovyContext.CURRENT, cx).call(gc::call);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
				if (target instanceof Runnable r && (args == null || args.length == 0)) {
					ScopedValue.where(KubeGroovyContext.CURRENT, cx).run(r::run);
					return null;
				}
				throw new UnsupportedOperationException("Cannot invoke " + method + " on " + target);
			}
		});
	}

	public static Map<?, ?> toMap(Object o) {
		if (o instanceof Map<?, ?> m) {
			return m;
		}
		return null;
	}
}
