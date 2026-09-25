package dev.uncandango.kubegroovy.integration.jei;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.event.EventResult;
import dev.uncandango.kubegroovy.recipe.viewer.RemoveRecipesKubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@SuppressWarnings({"rawtypes", "unchecked"})
public class JEIRemoveRecipesKubeEvent implements RemoveRecipesKubeEvent {
	private final IRecipeManager recipeManager;
	private final Map<Identifier, IRecipeCategory> categories;
	private final Set<Identifier> removedGlobal;
	private final Map<IRecipeCategory, Collection<Identifier>> removed;

	public JEIRemoveRecipesKubeEvent(IRecipeManager recipeManager, Map<Identifier, IRecipeCategory<?>> categories) {
		this.recipeManager = recipeManager;
		this.categories = (Map) categories;
		this.removedGlobal = new HashSet<>();
		this.removed = new Reference2ObjectOpenHashMap<>();
	}

	@Override
	public void remove(Identifier[] recipesToRemove) {
		var cx = Context.getCurrent();
		for (var cat : categories.values()) {
			removed.computeIfAbsent(cat, _0 -> new HashSet<>()).addAll(Arrays.asList(recipesToRemove));
		}
	}

	@Override
	public void removeFromCategory(@Nullable Identifier category, Identifier[] recipesToRemove) {
		var cx = Context.getCurrent();
		if (category == null) {
			remove(recipesToRemove);
			return;
		}

		var cat = categories.get(category);

		if (cat == null) {
			KubeGroovy.LOGGER.info("Failed to remove recipes for type '{}': Category doesn't exist! Use event.categories to get a list of all categories.", category);
			return;
		}

		removed.computeIfAbsent(cat, _0 -> new HashSet<>()).addAll(Arrays.asList(recipesToRemove));
	}

	@Override
	public void afterPosted(EventResult result) {
		for (var cat : categories.values()) {
			var removedCat = removed.get(cat);

			if ((removedCat == null || removedCat.isEmpty()) && removedGlobal.isEmpty()) {
				continue;
			}

			var allRecipes = recipeManager.createRecipeLookup(cat.getRecipeType()).get().toList();
			var removedRecipes = new ArrayList<>();

			for (var recipe : allRecipes) {
				var id = cat.getIdentifier(recipe);

				if (id != null && ((removedCat != null && removedCat.contains(id)) || removedGlobal.contains(id))) {
					removedRecipes.add(recipe);
				}
			}

			if (!removedRecipes.isEmpty()) {
				recipeManager.hideRecipes(cat.getRecipeType(), removedRecipes);
			}
		}
	}
}