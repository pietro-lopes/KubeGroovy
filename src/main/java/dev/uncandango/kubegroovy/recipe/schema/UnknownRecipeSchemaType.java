package dev.uncandango.kubegroovy.recipe.schema;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeSerializer;

public class UnknownRecipeSchemaType extends RecipeSchemaType {
	public UnknownRecipeSchemaType(RecipeNamespace namespace, Identifier id, RecipeSerializer<?> serializer) {
		super(namespace, id, UnknownRecipeSchema.SCHEMA, serializer);
	}
}
