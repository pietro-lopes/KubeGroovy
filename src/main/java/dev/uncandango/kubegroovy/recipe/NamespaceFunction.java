package dev.uncandango.kubegroovy.recipe;

import dev.uncandango.kubegroovy.recipe.schema.RecipeNamespace;
import dev.uncandango.kubegroovy.util.WrappedGY;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;

import java.util.Map;

public class NamespaceFunction implements WrappedGY {
	private final RecipeNamespace namespace;
	private final Map<String, RecipeTypeFunction> map;

	public NamespaceFunction(RecipeNamespace namespace, Map<String, RecipeTypeFunction> map) {
		this.namespace = namespace;
		this.map = map;
	}

	public Object call(Object... args0) {
		if (args0.length == 0) {
			return namespace.keySet();
		}
		if (args0.length == 1) {
			return map.get(args0[0]);
		}
		throw  new IllegalArgumentException("Namespace function has to be passed as arguments");
	}

	/// Resolves a recipe callable without replacing ordinary Java properties.
	public RecipeTypeFunction propertyMissing(String name) {
		var function = map.get(name);
		if (function == null) {
			throw new MissingPropertyException(name, getClass());
		}
		return function;
	}

	/// Dispatches dotted recipe calls after ordinary Java method resolution.
	public Object methodMissing(String name, Object args) {
		Object[] arguments = args == null ? new Object[0] : args instanceof Object[] array ? array : new Object[]{args};
		var function = map.get(name);
		if (function == null) {
			throw new MissingMethodException(name, getClass(), arguments);
		}
		return function.call(arguments);
	}

	@Override
	public String toString() {
		return namespace.name;
	}

	public String getMod() {
		return namespace.name;
	}

	@Override
	public int hashCode() {
		return namespace.name.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return namespace.name.equals(obj.toString());
	}
}