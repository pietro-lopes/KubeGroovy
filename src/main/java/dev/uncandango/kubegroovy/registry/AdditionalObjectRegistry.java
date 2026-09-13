package dev.uncandango.kubegroovy.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

@FunctionalInterface
public interface AdditionalObjectRegistry {
	<T> void add(ResourceKey<Registry<T>> registry, BuilderBase<? extends T> builder);
}
