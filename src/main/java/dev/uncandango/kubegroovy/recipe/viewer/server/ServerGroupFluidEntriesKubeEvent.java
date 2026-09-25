package dev.uncandango.kubegroovy.recipe.viewer.server;

import dev.uncandango.kubegroovy.fluid.FluidWrapper;
import dev.uncandango.kubegroovy.recipe.viewer.GroupEntriesKubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

public class ServerGroupFluidEntriesKubeEvent implements GroupEntriesKubeEvent {
	private final List<FluidData.Group> list;

	public ServerGroupFluidEntriesKubeEvent(List<FluidData.Group> list) {
		this.list = list;
	}

	@Override
	public void group(Object filter, Identifier groupId, Component description) {
		var cx = Context.getCurrent();
		list.add(new FluidData.Group(FluidWrapper.wrapIngredient(filter), groupId, description));
	}
}
