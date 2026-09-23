package dev.uncandango.kubegroovy.script.bridge;

import dev.uncandango.kubegroovy.script.KubeGroovyContext;
import groovy.lang.Closure;
import dev.uncandango.kubegroovy.script.type.TypeInfo;

/**
 * Legacy-compat function holder. In Groovy port, script callbacks are Closures or
 * Java functional interfaces. This class wraps either for source compat.
 */
public class BaseFunction { //  implements Scriptable
	protected final Object wrapped;

	public BaseFunction() {
		this.wrapped = null;
	}

	public BaseFunction(Closure<?> closure) {
		this.wrapped = closure;
	}

	public BaseFunction(java.util.function.Function<Object[], Object> fn) {
		this.wrapped = fn;
	}

	/** Groovy's normal callable protocol for a bound function/event object. */
	public Object call(Object... args) {
		Context cx = Context.getCurrent();
		if (wrapped instanceof Closure<?> c) {
			return c.call(args == null ? new Object[0] : args);
		}
		if (wrapped instanceof java.util.function.Function<?, ?> fn) {
			@SuppressWarnings("unchecked")
			var f = (java.util.function.Function<Object[], Object>) fn;
			return f.apply(args);
		}

//		if (value instanceof CustomJavaToGyWrapper wrapper && cx instanceof KubeGroovyContext kgy) {
//			return wrapper.convertJavaToGy(cx, kgy.topLevelScope, TypeInfo.of(value.getClass()));
//		}
		return args;
	}

	public Object getWrapped() {
		return wrapped;
	}
}
