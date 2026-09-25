package dev.uncandango.kubegroovy.integration.jei;

import dev.uncandango.kubegroovy.recipe.viewer.RemoveCategoriesKubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.resources.Identifier;

import java.util.Map;

public class JEIRemoveCategoriesKubeEvent implements RemoveCategoriesKubeEvent {
	private final IRecipeManager recipeManager;
	private final Map<Identifier, IRecipeCategory<?>> categories;

	public JEIRemoveCategoriesKubeEvent(IRecipeManager recipeManager, Map<Identifier, IRecipeCategory<?>> categories) {
		this.recipeManager = recipeManager;
		this.categories = categories;
	}

	@Override
	public void remove(Identifier[] ids) {
		var cx = Context.getCurrent();
		for (var c : ids) {
			var category = categories.get(c);

			if (category != null) {
				recipeManager.hideRecipeCategory(category.getRecipeType());
				categories.remove(c);
			}
		}
	}
}