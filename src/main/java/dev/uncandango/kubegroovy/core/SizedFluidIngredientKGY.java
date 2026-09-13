package dev.uncandango.kubegroovy.core;

import com.google.gson.JsonElement;
import dev.uncandango.kubegroovy.codec.KubeGroovyCodecs;
import dev.uncandango.kubegroovy.fluid.FluidWrapper;
import dev.uncandango.kubegroovy.recipe.RecipeScriptContext;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.recipe.match.FluidMatch;
import dev.uncandango.kubegroovy.recipe.match.Replaceable;
import dev.uncandango.kubegroovy.util.Cast;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

@RemapPrefixForGY("kgy$")
public interface SizedFluidIngredientKGY extends Replaceable, FluidMatch {
	default SizedFluidIngredient kgy$self() {
		return Cast.to(this);
	}

	@Override
	default Object replaceThisWith(RecipeScriptContext cx, Object with) {
		var ingredient = FluidWrapper.wrapIngredient(cx.cx(), with);

		if (!ingredient.equals(kgy$self().ingredient())) {
			return new SizedFluidIngredient(ingredient, kgy$self().amount());
		}

		return this;
	}

	default JsonElement kgy$toFlatJson() {
		return KubeGroovyCodecs.toJsonOrThrow(kgy$self(), SizedFluidIngredient.CODEC);
	}

	default JsonElement kgy$toNestedJson() {
		return KubeGroovyCodecs.toJsonOrThrow(kgy$self(), SizedFluidIngredient.CODEC);
	}

	@Override
	default boolean matches(RecipeMatchContext cx, FluidStack s, boolean exact) {
		return ((FluidMatch) kgy$self().ingredient()).matches(cx, s, exact);
	}

	@Override
	default boolean matches(RecipeMatchContext cx, FluidIngredient in, boolean exact) {
		return ((FluidMatch) kgy$self().ingredient()).matches(cx, in, exact);
	}
}
