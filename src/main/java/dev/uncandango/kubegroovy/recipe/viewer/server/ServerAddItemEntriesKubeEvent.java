package dev.uncandango.kubegroovy.recipe.viewer.server;

import dev.uncandango.kubegroovy.plugin.builtin.wrapper.ItemWrapper;
import dev.uncandango.kubegroovy.recipe.viewer.AddEntriesKubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ServerAddItemEntriesKubeEvent implements AddEntriesKubeEvent {
	private final List<ItemStack> list;

	public ServerAddItemEntriesKubeEvent(List<ItemStack> list) {
		this.list = list;
	}

	@Override
	public void add(Context cx, Object[] items) {
		for (var item : items) {
			list.add(ItemWrapper.wrap(cx, item));
		}
	}
}
