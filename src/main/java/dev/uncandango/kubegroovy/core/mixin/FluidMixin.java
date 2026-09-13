package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.FluidKGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = Fluid.class, priority = 1001)
@RemapPrefixForGY("kgy$")
public abstract class FluidMixin implements FluidKGY {
	@Shadow
	@Final
	private Holder.Reference<Fluid> builtInRegistryHolder;

	@Unique
	private @Nullable ResourceKey<Fluid> kgy$registryKey;

	@Unique
	private @Nullable String kgy$id;

	@Override
	public Holder.Reference<Fluid> kgy$asHolder() {
		return builtInRegistryHolder;
	}

	@Override
	public ResourceKey<Fluid> kgy$getKey() {
		if (kgy$registryKey == null) {
			kgy$registryKey = FluidKGY.super.kgy$getKey();
		}

		return kgy$registryKey;
	}

	@Override
	public String kgy$getId() {
		if (kgy$id == null) {
			kgy$id = FluidKGY.super.kgy$getId();
		}

		return kgy$id;
	}
}
