package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.fluid.FluidLike;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidType;

@RemapPrefixForGY("kgy$")
public interface FluidKGY extends RegistryObjectKGY<Fluid>, FluidLike {
	@Override
	default Fluid kgy$getFluid() {
		return (Fluid) this;
	}

	@Override
	default ResourceKey<Registry<Fluid>> kgy$getRegistryId() {
		return Registries.FLUID;
	}

	@Override
	default Registry<Fluid> kgy$getRegistry() {
		return BuiltInRegistries.FLUID;
	}

	@Override
	default int kgy$getAmount() {
		return FluidType.BUCKET_VOLUME;
	}

	@Override
	default boolean kgy$isEmpty() {
		return kgy$getFluid().isSame(Fluids.EMPTY);
	}
}
