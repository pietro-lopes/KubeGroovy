package dev.uncandango.kubegroovy.error;

import dev.uncandango.kubegroovy.recipe.component.RecipeComponent;

public class EmptyRecipeComponentException extends InvalidRecipeComponentValueException {
	public EmptyRecipeComponentException(RecipeComponent<?> component, Object value) {
		super("Component '" + component.toString() + "' is not allowed to be empty!", component, value);
	}
}
