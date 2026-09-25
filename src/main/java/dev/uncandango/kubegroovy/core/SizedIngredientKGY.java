package dev.uncandango.kubegroovy.core;

import com.google.gson.JsonElement;
import dev.uncandango.kubegroovy.codec.KubeGroovyCodecs;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.IngredientWrapper;
import dev.uncandango.kubegroovy.recipe.RecipeScriptContext;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.recipe.match.ItemMatch;
import dev.uncandango.kubegroovy.recipe.match.Replaceable;
import dev.uncandango.kubegroovy.util.Cast;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

public interface SizedIngredientKGY extends Replaceable, IngredientSupplierKGY, ItemMatch {
	default SizedIngredient kgy$self() {
		return Cast.to(this);
	}

	@Override
	default Object replaceThisWith(RecipeScriptContext cx, Object with) {
		var ingredient = IngredientWrapper.wrap(with);

		if (!ingredient.equals(kgy$self().ingredient())) {
			return new SizedIngredient(ingredient, kgy$self().count());
		}

		return this;
	}

	@Override
	default Ingredient kgy$asIngredient() {
		return kgy$self().ingredient();
	}

	@Override
	default boolean matches(RecipeMatchContext cx, ItemStack item, boolean exact) {
		return kgy$self().ingredient().matches(cx, item, exact);
	}

	@Override
	default boolean matches(RecipeMatchContext cx, Ingredient in, boolean exact) {
		return kgy$self().ingredient().matches(cx, in, exact);
	}

	default JsonElement kgy$toNestedJson() {
		return KubeGroovyCodecs.toJsonOrThrow(kgy$self(), SizedIngredient.NESTED_CODEC);
	}
}
