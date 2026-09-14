package dev.uncandango.kubegroovy.script;

import dev.uncandango.kubegroovy.script.bridge.Scriptable;
import groovy.lang.Binding;

import java.util.function.Supplier;

public record BindingRegistry(KubeGroovyContext context, Binding binding, Scriptable scope) {
	public BindingRegistry(KubeGroovyContext context, Scriptable scope) {
		this(context, context == null ? new Binding() : context.binding, scope);
	}

	public ScriptType type() {
		return context.getType();
	}

	/// Adds a value to the script scope when it is not null.
	public void add(String name, Object value) {
		if (value != null) {
			try {
				binding.setVariable(name, context.javaToGY(value, scope));
			} catch (Exception e) {
				binding.setVariable(name, value);
			}
			context.addToScope(scope, name, value);
		}
	}

	/// Adds a lazily-resolved binding. Resolved immediately if available, otherwise skipped.
	public void addLazy(String name, Supplier<?> supplier) {
		try {
			var value = supplier.get();
			if (value != null) {
				add(name, value instanceof Class<?> c ? context.loadJavaClass(c.getName(), false) : value);
			}
			// Nulls are skipped for the minimal Groovy binding bridge (no getter traps).
		} catch (Exception ignored) {
		}
	}
}
