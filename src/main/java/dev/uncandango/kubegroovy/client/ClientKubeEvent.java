package dev.uncandango.kubegroovy.client;

import dev.uncandango.kubegroovy.event.KubeEvent;
import net.minecraft.client.Minecraft;

public interface ClientKubeEvent extends KubeEvent {
	default Minecraft getClient() {
		return Minecraft.getInstance();
	}
}
