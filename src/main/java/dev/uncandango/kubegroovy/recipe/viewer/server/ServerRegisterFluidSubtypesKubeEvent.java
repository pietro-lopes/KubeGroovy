package dev.uncandango.kubegroovy.recipe.viewer.server;

import dev.uncandango.kubegroovy.fluid.FluidWrapper;
import dev.uncandango.kubegroovy.recipe.viewer.RegisterSubtypesKubeEvent;
import dev.uncandango.kubegroovy.recipe.viewer.SubtypeInterpreter;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.core.component.DataComponentType;

import java.util.List;

public class ServerRegisterFluidSubtypesKubeEvent implements RegisterSubtypesKubeEvent {
	private final List<FluidData.DataComponentSubtypes> list;

	public ServerRegisterFluidSubtypesKubeEvent(List<FluidData.DataComponentSubtypes> list) {
		this.list = list;
	}

	@Override
	public void register(Object filter, SubtypeInterpreter interpreter) {
		var cx = Context.getCurrent();
		throw new UnsupportedOperationException("Not available on server side!");
	}

	@Override
	public void useComponents(Object filter, List<DataComponentType<?>> components) {
		var cx = Context.getCurrent();
		list.add(new FluidData.DataComponentSubtypes(FluidWrapper.wrapIngredient(filter), components));
	}
}
