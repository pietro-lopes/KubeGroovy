package dev.uncandango.kubegroovy.recipe.schema;

import dev.uncandango.kubegroovy.util.RegistryAccessContainer;

public interface RegistryAwareSchema {
	RecipeSchema create(RegistryAccessContainer cx);
}
