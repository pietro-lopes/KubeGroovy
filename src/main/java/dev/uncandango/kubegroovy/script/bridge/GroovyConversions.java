package dev.uncandango.kubegroovy.script.bridge;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.script.KubeGroovyContext;
import dev.uncandango.kubegroovy.script.groovy.KubeGroovyMetaClassCreationHandle;
import groovy.lang.Closure;
import groovy.lang.GString;
import groovy.lang.GroovyCallable;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

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
		return value;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Object toJava(Object from, Class<?> target) {
		if (from == null || target == null || target == Object.class) {
			return from;
		}

		// Groovy interpolated strings are GString, normalize to String early
		if (from instanceof GString gs) {
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
			if (target == BigInteger.class && from instanceof Number n) {
				return BigInteger.valueOf(n.longValue());
			}
			if (target == BigDecimal.class && from instanceof Number n) {
				return BigDecimal.valueOf(n.doubleValue());
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
			if (from instanceof Object[] arr && Collection.class.isAssignableFrom(target)) {
				return toCollection(Arrays.asList(arr), target);
			}
			if (from instanceof Iterable<?> it && Collection.class.isAssignableFrom(target)) {
				var out = new ArrayList<>();
				for (var e : it) {
					out.add(e);
				}
				return toCollection(out, target);
			}
			if (target.isArray() && from instanceof Collection<?> coll) {
				var comp = target.getComponentType();
				var arr = Array.newInstance(comp, coll.size());
				int i = 0;
				for (var e : coll) {
					Array.set(arr, i++, toJava(e, comp));
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
				return Enum.valueOf((Class<Enum>) target, name.toUpperCase(Locale.ROOT));
			}
			// Functional interfaces from closures
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
		final Object target = fn;
		if (!type.isInterface() || type.getMethods().length == 0) {
			return (T) target;
		}
		// Single-abstract-method proxy: call Closure/callable with args
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, new InvocationHandler() {
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
//				if (args != null) {
//					for (Object argument : args) {
//						KubeGroovyMetaClassCreationHandle.refreshIfNeeded(argument);
//					}
//				}
				if (method.getDeclaringClass() == Object.class) {
					return ScopedValue.where(KubeGroovyContext.CURRENT, cx).call(() -> method.invoke(target, args));
				}
				if (method.isDefault()) {
					return ScopedValue.where(KubeGroovyContext.CURRENT, cx).call(() -> {
						try {
							return InvocationHandler.invokeDefault(proxy, method, args == null ? new Object[0] : args);
						} catch (RuntimeException | Error error) {
							throw error;
						} catch (Throwable error) {
							throw new RuntimeException(error);
						}
					});
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
				if (target instanceof Function<?, ?> f) {
					@SuppressWarnings("rawtypes")
					var func = (Function) f;
					return ScopedValue.where(KubeGroovyContext.CURRENT, cx).call(() -> func.apply(args == null || args.length == 0 ? null : args[0]));
				}
				if (target instanceof GroovyCallable<?> gc) {
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
