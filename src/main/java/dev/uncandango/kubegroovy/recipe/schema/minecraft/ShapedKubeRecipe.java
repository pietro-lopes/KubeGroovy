package dev.uncandango.kubegroovy.recipe.schema.minecraft;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.recipe.KubeRecipe;
import dev.uncandango.kubegroovy.recipe.RecipeTypeFunction;
import dev.uncandango.kubegroovy.recipe.schema.KubeRecipeFactory;
import dev.uncandango.kubegroovy.recipe.special.KubeGroovyCraftingRecipe;

public class ShapedKubeRecipe extends KubeRecipe {
	public static final KubeRecipeFactory RECIPE_FACTORY = new KubeRecipeFactory(KubeGroovy.id("shaped"), ShapedKubeRecipe.class, ShapedKubeRecipe::new);

	@Override
	public RecipeTypeFunction getSerializationTypeFunction() {
		// Use vanilla shaped recipe type if KubeGroovy is not needed
		if (type == type.event.shaped // if this type == KubeGroovy:shaped
			&& type.event.shaped != type.event.vanillaShaped // check if not in serverOnly mode
			&& !json.has(KubeGroovyCraftingRecipe.INGREDIENT_ACTIONS_KEY)
			&& !json.has(KubeGroovyCraftingRecipe.MODIFY_RESULT_KEY)
			&& !json.has(KubeGroovyCraftingRecipe.MIRROR_KEY)
		) {
			return type.event.vanillaShaped;
		}

		return super.getSerializationTypeFunction();
	}
}
