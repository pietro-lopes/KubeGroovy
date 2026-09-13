package dev.uncandango.kubegroovy.recipe.match;

import dev.uncandango.kubegroovy.recipe.RecipeScriptContext;

public interface Replaceable {
	default Object replaceThisWith(RecipeScriptContext cx, Object with) {
		return this;
	}
}
