package dev.uncandango.kubegroovy.core;

import net.minecraft.world.item.crafting.Ingredient;

public interface IngredientSupplierKGY {
	default Ingredient kgy$asIngredient() {
		throw new NoMixinException();
	}
}
