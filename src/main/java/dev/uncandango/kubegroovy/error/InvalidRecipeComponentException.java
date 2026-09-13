package dev.uncandango.kubegroovy.error;

import dev.uncandango.kubegroovy.recipe.component.RecipeComponentValue;

public class InvalidRecipeComponentException extends RecipeComponentException {
	public final RecipeComponentValue<?> componentValueHolder;

	public InvalidRecipeComponentException(RecipeComponentValue<?> h, Throwable cause) {
		super("Invalid component " + h.key.name + ": " + h.key.component.toString(), cause, h);
		this.componentValueHolder = h;
	}
}
