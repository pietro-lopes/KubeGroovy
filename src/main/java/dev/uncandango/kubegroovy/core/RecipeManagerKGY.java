package dev.uncandango.kubegroovy.core;

import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;

import java.util.Collection;

public interface RecipeManagerKGY extends ReloadableServerResourceHolderKGY {
	default void kgy$replaceRecipes(RecipeMap recipeMap) {
		throw new NoMixinException();
	}

	default Collection<RecipeHolder<?>> kgy$getRecipes() {
		throw new NoMixinException();
	}
}