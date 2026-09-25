package dev.uncandango.kubegroovy.recipe.viewer;

import dev.uncandango.kubegroovy.event.KubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.core.component.DataComponentType;

import java.util.List;

public interface RegisterSubtypesKubeEvent extends KubeEvent {
	void register(Object filter, SubtypeInterpreter interpreter);

	default void useComponents(Object filter) {
		var cx = Context.getCurrent();
		useComponents(filter, List.of());
	}

	void useComponents(Object filter, List<DataComponentType<?>> components);
}
