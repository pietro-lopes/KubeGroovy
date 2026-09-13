package dev.uncandango.kubegroovy.recipe.schema.function;

import net.minecraft.resources.Identifier;

@FunctionalInterface
public interface CustomRecipeSchemaFunctionRegistry {
	void register(Identifier id, ResolvedRecipeSchemaFunction function);
}
