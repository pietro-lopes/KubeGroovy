package dev.uncandango.kubegroovy.recipe.schema.function;

import dev.uncandango.kubegroovy.recipe.RecipeScriptContext;
import dev.uncandango.kubegroovy.recipe.component.RecipeComponent;

import java.util.List;

@FunctionalInterface
public interface ResolvedRecipeSchemaFunction {
	default List<RecipeComponent<?>> arguments() {
		return List.of();
	}

	void execute(RecipeScriptContext cx, List<Object> args);
}
