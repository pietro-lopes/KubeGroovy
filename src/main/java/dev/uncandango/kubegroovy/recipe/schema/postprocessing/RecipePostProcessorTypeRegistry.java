package dev.uncandango.kubegroovy.recipe.schema.postprocessing;

@FunctionalInterface
public interface RecipePostProcessorTypeRegistry {
	void register(RecipePostProcessorType<?> type);
}
