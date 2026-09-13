package dev.uncandango.kubegroovy.recipe;

import dev.uncandango.kubegroovy.recipe.component.RecipeValidationContext;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.script.WithScriptContext;
import dev.uncandango.kubegroovy.util.ErrorStack;
import dev.uncandango.kubegroovy.script.bridge.Context;

public interface RecipeScriptContext extends WithScriptContext, RecipeValidationContext, KubeRecipeContext, RecipeMatchContext {
	record Impl(Context cx, KubeRecipe recipe, ErrorStack errors) implements RecipeScriptContext {
		public Impl(Context cx, KubeRecipe recipe) {
			this(cx, recipe, ErrorStack.NONE);
		}
	}
}
