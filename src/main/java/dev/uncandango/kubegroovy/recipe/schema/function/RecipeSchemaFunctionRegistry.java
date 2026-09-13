package dev.uncandango.kubegroovy.recipe.schema.function;

@FunctionalInterface
public interface RecipeSchemaFunctionRegistry {
	void register(RecipeSchemaFunctionType<?> type);
}
