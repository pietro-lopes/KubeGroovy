package dev.uncandango.kubegroovy.recipe.viewer.server;

import dev.uncandango.kubegroovy.plugin.builtin.wrapper.IngredientWrapper;
import dev.uncandango.kubegroovy.recipe.viewer.RegisterSubtypesKubeEvent;
import dev.uncandango.kubegroovy.recipe.viewer.SubtypeInterpreter;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.core.component.DataComponentType;

import java.util.List;

public class ServerRegisterItemSubtypesKubeEvent implements RegisterSubtypesKubeEvent {
	private final List<ItemData.DataComponentSubtypes> list;

	public ServerRegisterItemSubtypesKubeEvent(List<ItemData.DataComponentSubtypes> list) {
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
		list.add(new ItemData.DataComponentSubtypes(IngredientWrapper.wrap(filter), components));
	}
}
