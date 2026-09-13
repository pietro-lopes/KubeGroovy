package dev.uncandango.kubegroovy.core;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;

public interface EntityTypeKGY extends RegistryObjectKGY<EntityType<?>> {
	@Override
	default ResourceKey<Registry<EntityType<?>>> kgy$getRegistryId() {
		return Registries.ENTITY_TYPE;
	}

	@Override
	default Registry<EntityType<?>> kgy$getRegistry() {
		return BuiltInRegistries.ENTITY_TYPE;
	}
}
