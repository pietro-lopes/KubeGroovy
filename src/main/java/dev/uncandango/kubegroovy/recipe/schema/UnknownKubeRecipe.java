package dev.uncandango.kubegroovy.recipe.schema;

import com.mojang.serialization.JsonOps;
import dev.uncandango.kubegroovy.CommonProperties;
import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.recipe.KubeRecipe;
import dev.uncandango.kubegroovy.recipe.RecipeScriptContext;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.recipe.match.ItemMatch;
import dev.uncandango.kubegroovy.recipe.match.ReplacementMatchInfo;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

public class UnknownKubeRecipe extends KubeRecipe {
	public static final KubeRecipeFactory RECIPE_FACTORY = new KubeRecipeFactory(KubeGroovy.id("unknown"), UnknownKubeRecipe.class, UnknownKubeRecipe::new);

	@Override
	public void deserialize(boolean merge) {
	}

	@Override
	public void serialize() {
	}

	@Override
	public boolean hasInput(RecipeMatchContext cx, ReplacementMatchInfo match) {
		if (CommonProperties.get().matchJsonRecipes && match.match() instanceof ItemMatch m) {
			var recipeJson = json;
			if (recipeJson == null) {
				return false;
			}

			if (recipeJson.has("ingredients") && recipeJson.get("ingredients").isJsonArray()) {
				var arr = recipeJson.getAsJsonArray("ingredients");
				if (arr.isEmpty()) {
					return false;
				}

				for (var el : arr) {
					if (el == null || el.isJsonNull()) {
						continue;
					}

					var ingredient = Ingredient.CODEC.parse(JsonOps.INSTANCE, el).result().orElse(null);
					if (ingredient != null && !ingredient.isEmpty() && ingredient.kgy$canBeUsedForMatching() && m.matches(cx, ingredient, match.exact())) {
						return true;
					}
				}

				return false;
			}

			if (recipeJson.has("ingredient")) {
				var ingredient = Ingredient.CODEC.parse(JsonOps.INSTANCE, recipeJson.get("ingredient")).result().orElse(null);
				return ingredient != null && !ingredient.isEmpty() && ingredient.kgy$canBeUsedForMatching() && m.matches(cx, ingredient, match.exact());
			}
		}

		return false;
	}

	@Override
	public boolean replaceInput(RecipeScriptContext cx, ReplacementMatchInfo match, Object with) {
		return false;
	}

	@Override
	public boolean hasOutput(RecipeMatchContext cx, ReplacementMatchInfo match) {
		if (CommonProperties.get().matchJsonRecipes && match.match() instanceof ItemMatch m) {
			var original = getOriginalRecipe();
			if (original == null) {
				return false;
			}

			var ctx = new ContextMap.Builder()
				.withOptionalParameter(SlotDisplayContext.REGISTRIES, type.event.registries)
				.create(SlotDisplayContext.CONTEXT);

			var displays = original.display();
			if (displays.isEmpty()) {
				return false;
			}

			for (var d : displays) {
				for (var stack : d.result().resolveForStacks(ctx)) {
					if (!stack.isEmpty() && m.matches(cx, stack, match.exact())) {
						return true;
					}
				}
			}
		}

		return false;
	}

	@Override
	public boolean replaceOutput(RecipeScriptContext cx, ReplacementMatchInfo match, Object with) {
		return false;
	}
}