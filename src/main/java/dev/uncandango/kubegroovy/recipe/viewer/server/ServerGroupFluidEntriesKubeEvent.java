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
	public void group(Context cx, Object filter, Identifier groupId, Component description) {
		list.add(new FluidData.Group(FluidWrapper.wrapIngredient(cx, filter), groupId, description));
	}
}
