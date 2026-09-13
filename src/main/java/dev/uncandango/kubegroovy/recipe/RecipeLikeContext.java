package dev.uncandango.kubegroovy.recipe;

import dev.uncandango.kubegroovy.core.RecipeLikeKGY;
import dev.uncandango.kubegroovy.util.RegistryAccessContainer;
import dev.uncandango.kubegroovy.util.RegistryOpsContainer;

public interface RecipeLikeContext {
	RegistryAccessContainer registries();

	RegistryOpsContainer ops();

	RecipeLikeKGY recipe();
}
