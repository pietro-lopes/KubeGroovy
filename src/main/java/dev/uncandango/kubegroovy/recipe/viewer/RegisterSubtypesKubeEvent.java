package dev.uncandango.kubegroovy.recipe.viewer;

import dev.uncandango.kubegroovy.event.KubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.core.component.DataComponentType;

import java.util.List;

public interface RegisterSubtypesKubeEvent extends KubeEvent {
	void register(Context cx, Object filter, SubtypeInterpreter interpreter);

	default void useComponents(Context cx, Object filter) {
		useComponents(cx, filter, List.of());
	}

	void useComponents(Context cx, Object filter, List<DataComponentType<?>> components);
}
