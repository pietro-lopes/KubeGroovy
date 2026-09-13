package dev.uncandango.kubegroovy.fluid;

import dev.uncandango.kubegroovy.KubeGroovy;
import net.neoforged.neoforge.fluids.crafting.FluidIngredientType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public interface KubeGroovyFluidIngredients {
	DeferredRegister<FluidIngredientType<?>> REGISTRY = DeferredRegister.create(NeoForgeRegistries.FLUID_INGREDIENT_TYPES, KubeGroovy.MOD_ID);

	Supplier<FluidIngredientType<?>> REGEX = REGISTRY.register("regex", () -> new FluidIngredientType<>(RegExFluidIngredient.CODEC, RegExFluidIngredient.STREAM_CODEC));
	Supplier<FluidIngredientType<?>> NAMESPACE = REGISTRY.register("namespace", () -> new FluidIngredientType<>(NamespaceFluidIngredient.CODEC, NamespaceFluidIngredient.STREAM_CODEC));
}
