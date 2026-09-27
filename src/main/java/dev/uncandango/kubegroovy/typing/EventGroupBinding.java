package dev.uncandango.kubegroovy.typing;

import dev.uncandango.kubegroovy.registry.RegistryKubeEvent;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import java.util.function.Consumer;

/** IDE receiver type for event-group bindings; the runtime value is an EventGroupWrapper. */
public interface EventGroupBinding<T> {
	static <T> void registry(ResourceKey<Registry<T>> registry, Consumer<RegistryKubeEvent<T>> consumer){};
}
