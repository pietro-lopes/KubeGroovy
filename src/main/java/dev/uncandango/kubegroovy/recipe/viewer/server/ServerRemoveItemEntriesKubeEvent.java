package dev.uncandango.kubegroovy.recipe.viewer.server;

import dev.uncandango.kubegroovy.plugin.builtin.wrapper.IngredientWrapper;
import dev.uncandango.kubegroovy.recipe.viewer.RemoveEntriesKubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

public class ServerRemoveItemEntriesKubeEvent implements RemoveEntriesKubeEvent {
	private final List<Ingredient> removedEntries;

	public ServerRemoveItemEntriesKubeEvent(List<Ingredient> removedEntries) {
		this.removedEntries = removedEntries;
	}

	@Override
	public void remove(Context cx, Object filter) {
		removedEntries.add(IngredientWrapper.wrap(cx, filter));
	}
}
