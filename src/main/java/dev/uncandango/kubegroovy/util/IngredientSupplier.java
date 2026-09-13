package dev.uncandango.kubegroovy.util;

import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Supplier;

@FunctionalInterface
public interface IngredientSupplier extends Supplier<Ingredient> {
	@Override
	Ingredient get();
}
