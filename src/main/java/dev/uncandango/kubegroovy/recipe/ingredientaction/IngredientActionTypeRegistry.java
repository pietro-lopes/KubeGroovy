package dev.uncandango.kubegroovy.recipe.ingredientaction;

@FunctionalInterface
public interface IngredientActionTypeRegistry {
	void register(IngredientActionType<?> type);
}
