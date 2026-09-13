package dev.uncandango.kubegroovy.integration.emi;
/*

import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.uncandango.kubegroovy.recipe.viewer.AddInformationKubeEvent;
import dev.uncandango.kubegroovy.recipe.viewer.RecipeViewerEntryType;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.util.List;

public class EMIAddInformationKubeEvent implements AddInformationKubeEvent {
	private final RecipeViewerEntryType type;
	private final EmiRegistry registry;

	public EMIAddInformationKubeEvent(RecipeViewerEntryType type, EmiRegistry registry) {
		this.type = type;
		this.registry = registry;
	}

	@Override
	public void add(Context cx, Object filter, List<Component> info) {
		var in = type.wrapPredicate(cx, filter);

		if (type == RecipeViewerEntryType.ITEM) {
			registry.addRecipe(new EmiInfoRecipe(List.of(EmiIngredient.of((Ingredient) in)), info, null));
		} else if (type == RecipeViewerEntryType.FLUID) {
			registry.addRecipe(new EmiInfoRecipe(List.of(EMIIntegration.fluidIngredient((FluidIngredient) in)), info, null));
		}
	}
}
*/
