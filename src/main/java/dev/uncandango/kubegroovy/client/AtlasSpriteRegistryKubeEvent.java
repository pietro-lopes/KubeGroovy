package dev.uncandango.kubegroovy.client;

import dev.uncandango.kubegroovy.event.KubeEvent;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

public class AtlasSpriteRegistryKubeEvent implements KubeEvent {
	private final Consumer<Identifier> registry;

	public AtlasSpriteRegistryKubeEvent(Consumer<Identifier> registry) {
		this.registry = registry;
	}

	public void register(Identifier id) {
		registry.accept(id);
	}
}
