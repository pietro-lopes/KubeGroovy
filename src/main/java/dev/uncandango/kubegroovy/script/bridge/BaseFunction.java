package dev.uncandango.kubegroovy.script.bridge;

import groovy.lang.Closure;

/**
 * Legacy-compat function holder. In Groovy port, script callbacks are Closures or
 * Java functional interfaces. This class wraps either for source compat.
 */
public class BaseFunction implements Scriptable {
	protected final Object wrapped;

	public BaseFunction() {
		this.wrapped = null;
	}

	public BaseFunction(Scriptable scope, Object prototype) {
		this.wrapped = null;
	}

	public BaseFunction(Closure<?> closure) {
		this.wrapped = closure;
	}

	public BaseFunction(java.util.function.Function<Object[], Object> fn) {
		this.wrapped = fn;
	}

	public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
		if (wrapped instanceof Closure<?> c) {
			return c.call((Object) args);
		}
		if (wrapped instanceof java.util.function.Function<?, ?> fn) {
			@SuppressWarnings("unchecked")
			var f = (java.util.function.Function<Object[], Object>) fn;
			return f.apply(args);
		}
		return null;
	}

	public Object getWrapped() {
		return wrapped;
	}
}
