package dev.uncandango.kubegroovy.recipe.viewer;

import dev.uncandango.kubegroovy.event.KubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public interface GroupEntriesKubeEvent extends KubeEvent {
	void group(Object filter, Identifier groupId, Component description);
}
