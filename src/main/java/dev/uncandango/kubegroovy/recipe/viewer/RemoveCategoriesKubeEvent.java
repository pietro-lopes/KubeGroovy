package dev.uncandango.kubegroovy.recipe.viewer;

import dev.uncandango.kubegroovy.event.KubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.resources.Identifier;

public interface RemoveCategoriesKubeEvent extends KubeEvent {
	void remove(Context cx, Identifier[] categories);
}
