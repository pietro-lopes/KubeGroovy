package dev.uncandango.kubegroovy.recipe.component;

import dev.uncandango.kubegroovy.recipe.KubeRecipe;
import dev.uncandango.kubegroovy.recipe.KubeRecipeContext;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.util.ErrorStack;

public interface RecipeValidationContext extends KubeRecipeContext, RecipeMatchContext {
	record Impl(KubeRecipe recipe, ErrorStack errors) implements RecipeValidationContext {
	}

	ErrorStack errors();
}
