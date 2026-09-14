package dev.uncandango.kubegroovy.script.bridge;

import groovy.lang.Closure;
import dev.uncandango.kubegroovy.script.KubeGroovyContext;
import dev.uncandango.kubegroovy.script.annotations.CustomJavaToGyWrapper;

/**
 * Legacy-compat function holder. In Groovy port, script callbacks are Closures or
 * Java functional interfaces. This class wraps either for source compat.
 */
public class BaseFunction implements Scriptable {
	protected final Object wrapped;
	private final Context originatingContext;

	public BaseFunction() {
		this.wrapped = null;
		this.originatingContext = Context.getCurrent();
	}

	public BaseFunction(Scriptable scope, Object prototype) {
		this.wrapped = null;
		this.originatingContext = Context.getCurrent();
	}

	public BaseFunction(Closure<?> closure) {
		this.wrapped = closure;
		this.originatingContext = Context.getCurrent();
	}

	public BaseFunction(java.util.function.Function<Object[], Object> fn) {
		this.wrapped = fn;
		this.originatingContext = Context.getCurrent();
	}

	public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
		if (wrapped instanceof Closure<?> c) {
			return c.call(args == null ? new Object[0] : args);
		}
		if (wrapped instanceof java.util.function.Function<?, ?> fn) {
			@SuppressWarnings("unchecked")
			var f = (java.util.function.Function<Object[], Object>) fn;
			return f.apply(args);
		}
		return null;
	}

	/** Groovy's normal callable protocol for a bound function/event object. */
	public Object call(Object... args) {
		Context previous = Context.getCurrent();
		Context previousActive = GroovyConversions.getActiveContext();
		// Delayed callbacks may run on a thread carrying an unrelated legacy
		// context. Prefer the context captured when this function was created;
		// only context-less functions inherit the caller's current context.
		Context cx = originatingContext != null ? originatingContext : previous;
		if (cx != null && cx != previous) {
			Context.setCurrent(cx);
		}
		if (cx != null) {
			GroovyConversions.pushActiveContext(cx);
		}
		try {
			Object value = call(cx, cx instanceof KubeGroovyContext kgy ? kgy.topLevelScope : null, null,
				args == null ? new Object[0] : args);
			if (value instanceof CustomJavaToGyWrapper wrapper && cx instanceof KubeGroovyContext kgy) {
				return wrapper.convertJavaToGy(cx, kgy.topLevelScope, TypeInfo.of(value.getClass()));
			}
			return value;
		} finally {
			GroovyConversions.pushActiveContext(previousActive);
			if (cx != null && cx != previous) {
				if (previous == null) Context.setCurrent(null); else Context.setCurrent(previous);
			}
		}
	}

	public Object getWrapped() {
		return wrapped;
	}
}
