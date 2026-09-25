package dev.uncandango.kubegroovy.recipe.viewer.server;

import dev.uncandango.kubegroovy.fluid.FluidWrapper;
import dev.uncandango.kubegroovy.recipe.viewer.RemoveEntriesKubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.util.List;

public class ServerRemoveFluidEntriesKubeEvent implements RemoveEntriesKubeEvent {
	private final List<FluidIngredient> removedEntries;

	public ServerRemoveFluidEntriesKubeEvent(List<FluidIngredient> removedEntries) {
		this.removedEntries = removedEntries;
	}

	@Override
	public void remove(Object filter) {
		var cx = Context.getCurrent();
		removedEntries.add(FluidWrapper.wrapIngredient(filter));
	}
}
