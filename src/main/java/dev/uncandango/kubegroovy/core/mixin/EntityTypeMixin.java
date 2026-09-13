package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.EntityTypeKGY;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityType.class)
public abstract class EntityTypeMixin implements EntityTypeKGY {
	@Shadow
	@Final
	private Holder.Reference<EntityType<?>> builtInRegistryHolder;

	@Unique
	private @Nullable ResourceKey<EntityType<?>> kgy$registryKey;

	@Unique
	private @Nullable String kgy$id;

	@Override
	public Holder<EntityType<?>> kgy$asHolder() {
		return builtInRegistryHolder;
	}

	@Override
	public ResourceKey<EntityType<?>> kgy$getKey() {
		if (kgy$registryKey == null) {
			kgy$registryKey = EntityTypeKGY.super.kgy$getKey();
		}

		return kgy$registryKey;
	}

	@Override
	public String kgy$getId() {
		if (kgy$id == null) {
			kgy$id = EntityTypeKGY.super.kgy$getId();
		}

		return kgy$id;
	}
}
