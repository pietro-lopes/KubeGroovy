package dev.uncandango.kubegroovy.recipe.filter;

import net.minecraft.resources.Identifier;

public record IDFilter(Identifier id) implements RecipeFilter {
	@Override
	public boolean test(RecipeMatchContext cx) {
		return cx.recipe().kgy$getOrCreateId().equals(id);
	}
}
