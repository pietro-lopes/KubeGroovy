package dev.uncandango.kubegroovy.recipe.schema.minecraft;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.recipe.KubeRecipe;
import dev.uncandango.kubegroovy.recipe.RecipeTypeFunction;
import dev.uncandango.kubegroovy.recipe.schema.KubeRecipeFactory;
import dev.uncandango.kubegroovy.recipe.special.KubeGroovyCraftingRecipe;

public class ShapelessKubeRecipe extends KubeRecipe {
	public static final KubeRecipeFactory RECIPE_FACTORY = new KubeRecipeFactory(KubeGroovy.id("shapeless"), ShapelessKubeRecipe.class, ShapelessKubeRecipe::new);

	@Override
	public RecipeTypeFunction getSerializationTypeFunction() {
		// Use vanilla shapeless recipe type if KubeGroovy is not needed
		if (type == type.event.shapeless // if this type == KubeGroovy:shapeless
			&& type.event.shapeless != type.event.vanillaShapeless // check if not in serverOnly mode
			&& !json.has(KubeGroovyCraftingRecipe.INGREDIENT_ACTIONS_KEY)
			&& !json.has(KubeGroovyCraftingRecipe.MODIFY_RESULT_KEY)
		) {
			return type.event.vanillaShapeless;
		}

		return super.getSerializationTypeFunction();
	}
}
