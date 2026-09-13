package dev.uncandango.kubegroovy.error;

import dev.uncandango.kubegroovy.recipe.RecipeKey;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

public class MissingComponentException extends KubeRuntimeException {
	public final @Nullable RecipeKey<?> key;
	public final Collection<RecipeKey<?>> valid;

	public MissingComponentException(String keyName, @Nullable RecipeKey<?> key, Collection<RecipeKey<?>> valid) {
		super("Recipe component key '" + keyName + "' not found! Valid keys: " + valid);
		this.key = key;
		this.valid = valid;
	}
}
