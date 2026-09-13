package dev.uncandango.kubegroovy.recipe.filter;

import dev.uncandango.kubegroovy.core.RecipeLikeKGY;
import dev.uncandango.kubegroovy.recipe.KubeRecipe;
import dev.uncandango.kubegroovy.recipe.RecipeLikeContext;
import dev.uncandango.kubegroovy.util.RegistryAccessContainer;
import dev.uncandango.kubegroovy.util.RegistryOpsContainer;
import dev.uncandango.kubegroovy.script.bridge.Context;

public interface RecipeMatchContext extends RecipeLikeContext {
	record Impl(Context cx, RegistryAccessContainer registries, RegistryOpsContainer ops, RecipeLikeKGY recipe) implements RecipeMatchContext {
		public Impl(Context cx, KubeRecipe recipe) {
			this(cx, recipe.type.event.registries, recipe.type.event.ops, recipe);
		}
	}
}
