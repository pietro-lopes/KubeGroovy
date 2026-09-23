package dev.uncandango.kubegroovy.recipe;

import dev.uncandango.kubegroovy.recipe.schema.RecipeNamespace;
import dev.uncandango.kubegroovy.util.WrappedGY;
import dev.uncandango.kubegroovy.script.bridge.BaseFunction;
import dev.uncandango.kubegroovy.script.bridge.Context;

import java.util.Map;

public class NamespaceFunction extends BaseFunction implements WrappedGY {
	private final RecipeNamespace namespace;
	private final Map<String, RecipeTypeFunction> map;

	public NamespaceFunction(RecipeNamespace namespace, Map<String, RecipeTypeFunction> map) {
		this.namespace = namespace;
		this.map = map;
	}

	@Override
	public Object call(Object[] args0) {
		if (args0.length == 0) {
			return namespace.keySet();
		}
		if (args0.length == 1) {
			return map.get(args0[0]);
		}
		throw  new IllegalArgumentException("Namespace function has to be passed as arguments");
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