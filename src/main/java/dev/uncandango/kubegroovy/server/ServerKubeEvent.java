package dev.uncandango.kubegroovy.server;

import dev.uncandango.kubegroovy.event.KubeEvent;
import net.minecraft.server.MinecraftServer;

public class ServerKubeEvent implements KubeEvent {
	public final MinecraftServer server;

	public ServerKubeEvent(MinecraftServer s) {
		server = s;
	}

	public MinecraftServer getServer() {
		return server;
	}
}