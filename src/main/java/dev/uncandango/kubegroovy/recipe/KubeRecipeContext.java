package dev.uncandango.kubegroovy.recipe;

import dev.uncandango.kubegroovy.util.RegistryAccessContainer;
import dev.uncandango.kubegroovy.util.RegistryOpsContainer;

public interface KubeRecipeContext extends RecipeLikeContext {
	@Override
	default RegistryAccessContainer registries() {
		return recipe().type.event.registries;
	}

	@Override
	default RegistryOpsContainer ops() {
		return recipe().type.event.ops;
	}

	@Override
	KubeRecipe recipe();
}
