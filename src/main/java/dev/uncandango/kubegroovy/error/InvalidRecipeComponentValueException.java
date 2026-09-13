package dev.uncandango.kubegroovy.error;

import dev.uncandango.kubegroovy.recipe.component.RecipeComponent;

public class InvalidRecipeComponentValueException extends RecipeComponentException {
	public final RecipeComponent<?> component;
	public final Object value;

	public InvalidRecipeComponentValueException(String message, RecipeComponent<?> component, Object value) {
		super(message, null, component, null, value);
		this.component = component;
		this.value = value;

		customData("component", component);
		customData("value", value);
	}

	public InvalidRecipeComponentValueException(RecipeComponent<?> component, Object value) {
		this("Component '" + component + "' has an invalid value!", component, value);
	}
}
