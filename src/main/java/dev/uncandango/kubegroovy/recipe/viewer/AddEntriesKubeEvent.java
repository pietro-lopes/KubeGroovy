package dev.uncandango.kubegroovy.recipe.viewer;

import dev.uncandango.kubegroovy.event.KubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;

public interface AddEntriesKubeEvent extends KubeEvent {
	void add(Object[] items);
}
