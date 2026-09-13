package dev.uncandango.kubegroovy.recipe.viewer;

import dev.uncandango.kubegroovy.event.KubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;

public interface RemoveEntriesKubeEvent extends KubeEvent {
	void remove(Context cx, Object filter);
}
