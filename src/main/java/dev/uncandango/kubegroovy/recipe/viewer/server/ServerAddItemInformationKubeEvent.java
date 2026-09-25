package dev.uncandango.kubegroovy.recipe.viewer.server;

import dev.uncandango.kubegroovy.plugin.builtin.wrapper.IngredientWrapper;
import dev.uncandango.kubegroovy.recipe.viewer.AddInformationKubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ServerAddItemInformationKubeEvent implements AddInformationKubeEvent {
	private final List<ItemData.Info> list;

	public ServerAddItemInformationKubeEvent(List<ItemData.Info> list) {
		this.list = list;
	}

	@Override
	public void add(Object filter, List<Component> info) {
		var cx = Context.getCurrent();
		list.add(new ItemData.Info(IngredientWrapper.wrap(filter), info));
	}
}
