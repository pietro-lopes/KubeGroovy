package dev.uncandango.kubegroovy.registry;

import com.mojang.serialization.Codec;
import dev.uncandango.kubegroovy.script.bridge.TypeInfo;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

public interface ServerRegistryRegistry {
	<T> void register(ResourceKey<? extends Registry<T>> registry, Codec<T> directCodec, TypeInfo typeInfo);

	default <T> void register(ResourceKey<Registry<T>> registry, Codec<T> directCodec, Class<T> type) {
		register(registry, directCodec, TypeInfo.of(type));
	}
}
