package dev.uncandango.kubegroovy.recipe.viewer.server;

import dev.uncandango.kubegroovy.fluid.FluidWrapper;
import dev.uncandango.kubegroovy.recipe.viewer.AddEntriesKubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

public class ServerAddFluidEntriesKubeEvent implements AddEntriesKubeEvent {
	private final List<FluidStack> list;

	public ServerAddFluidEntriesKubeEvent(List<FluidStack> list) {
		this.list = list;
	}

	@Override
	public void add(Context cx, Object[] items) {
		for (var item : items) {
			list.add(FluidWrapper.wrap(cx, item));
		}
	}
}
