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
	public void group(Object filter, Identifier groupId, Component description) {
		var cx = Context.getCurrent();
		list.add(new ItemData.Group(IngredientWrapper.wrap(filter), groupId, description));
	}
}
