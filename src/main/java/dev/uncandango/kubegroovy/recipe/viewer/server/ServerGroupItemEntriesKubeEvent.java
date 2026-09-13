package dev.uncandango.kubegroovy.recipe.viewer.server;

import dev.uncandango.kubegroovy.plugin.builtin.wrapper.IngredientWrapper;
import dev.uncandango.kubegroovy.recipe.viewer.GroupEntriesKubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

public class ServerGroupItemEntriesKubeEvent implements GroupEntriesKubeEvent {
	private final List<ItemData.Group> list;

	public ServerGroupItemEntriesKubeEvent(List<ItemData.Group> list) {
		this.list = list;
	}

	@Override
	public void group(Context cx, Object filter, Identifier groupId, Component description) {
		list.add(new ItemData.Group(IngredientWrapper.wrap(cx, filter), groupId, description));
	}
}
