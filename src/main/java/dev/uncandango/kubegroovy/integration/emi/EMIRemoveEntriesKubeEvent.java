package dev.uncandango.kubegroovy.integration.emi;
/*

import dev.emi.emi.api.EmiRegistry;
import dev.uncandango.kubegroovy.item.ItemPredicate;
import dev.uncandango.kubegroovy.recipe.viewer.RecipeViewerEntryType;
import dev.uncandango.kubegroovy.recipe.viewer.RemoveEntriesKubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

public class EMIRemoveEntriesKubeEvent implements RemoveEntriesKubeEvent {
	private final RecipeViewerEntryType type;
	private final EmiRegistry registry;

	public EMIRemoveEntriesKubeEvent(RecipeViewerEntryType type, EmiRegistry registry) {
		this.type = type;
		this.registry = registry;
	}

	@Override
	public void remove(Context cx, Object filter) {
		var predicate = type.wrapPredicate(cx, filter);

		if (type == RecipeViewerEntryType.ITEM) {
			registry.removeEmiStacks(EMIIntegration.predicate((ItemPredicate) predicate));
		} else if (type == RecipeViewerEntryType.FLUID) {
			registry.removeEmiStacks(EMIIntegration.predicate((FluidIngredient) predicate));
		}
	}
}
*/
