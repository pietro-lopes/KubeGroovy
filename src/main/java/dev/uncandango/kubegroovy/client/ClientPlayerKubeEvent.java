package dev.uncandango.kubegroovy.client;

import dev.uncandango.kubegroovy.player.KubePlayerEvent;
import net.minecraft.client.player.LocalPlayer;

public class ClientPlayerKubeEvent implements KubePlayerEvent, ClientKubeEvent {
	private final LocalPlayer player;

	public ClientPlayerKubeEvent(LocalPlayer player) {
		this.player = player;
	}

	@Override
	public LocalPlayer getEntity() {
		return player;
	}

	@Override
	public LocalPlayer getPlayer() {
		return player;
	}
}
