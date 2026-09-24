package dev.uncandango.kubegroovy.script;

import groovy.lang.Binding;

import java.util.function.Supplier;

public record BindingRegistry(KubeGroovyContext context, Binding binding) {
	public BindingRegistry(KubeGroovyContext context) {
		this(context, context == null ? new Binding() : context.binding);
	}

	public ScriptType type() {
		return context.getType();
	}

	/// Adds a value to the script scope when it is not null.
	public void add(String name, Object value) {
		if (value != null) {
			try {
				binding.setVariable("$" + name, value);
			} catch (Exception e) {
				binding.setVariable("$" + name, value);
			}
		}
	}

	/// Adds a lazily-resolved binding. Resolved immediately if available, otherwise skipped.
	public void addLazy(String name, Supplier<?> supplier) {
		try {
			var value = supplier.get();
			if (value != null) {
				add(name, value);
			}
			// Nulls are skipped for the minimal Groovy binding bridge (no getter traps).
		} catch (Exception ignored) {
		}
	}
}
