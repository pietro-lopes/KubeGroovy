package dev.uncandango.kubegroovy.recipe.match;

import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

public interface FluidMatch extends ReplacementMatch {
	boolean matches(RecipeMatchContext cx, FluidStack stack, boolean exact);

	boolean matches(RecipeMatchContext cx, FluidIngredient ingredient, boolean exact);

	boolean matches(RecipeMatchContext cx, SizedFluidIngredient sizedFluidIngredient, boolean exact);
}
