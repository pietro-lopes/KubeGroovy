package dev.uncandango.kubegroovy.recipe.viewer;

import dev.uncandango.kubegroovy.event.KubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.network.chat.Component;

import java.util.List;

public interface AddInformationKubeEvent extends KubeEvent {
	void add(Object filter, List<Component> info);
}
