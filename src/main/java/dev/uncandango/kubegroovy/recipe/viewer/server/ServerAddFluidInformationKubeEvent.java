package dev.uncandango.kubegroovy.recipe.viewer.server;

import dev.uncandango.kubegroovy.fluid.FluidWrapper;
import dev.uncandango.kubegroovy.recipe.viewer.AddInformationKubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ServerAddFluidInformationKubeEvent implements AddInformationKubeEvent {
	private final List<FluidData.Info> list;

	public ServerAddFluidInformationKubeEvent(List<FluidData.Info> list) {
		this.list = list;
	}

	@Override
	public void add(Context cx, Object filter, List<Component> info) {
		list.add(new FluidData.Info(FluidWrapper.wrapIngredient(cx, filter), info));
	}
}
