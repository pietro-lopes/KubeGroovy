package dev.uncandango.kubegroovy.fluid;

import dev.uncandango.kubegroovy.recipe.match.ReplacementMatch;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.world.level.material.Fluid;

@RemapPrefixForGY("kgy$")
public interface FluidLike extends ReplacementMatch {
	int kgy$getAmount();

	Fluid kgy$getFluid();

	default boolean kgy$isEmpty() {
		return kgy$getAmount() <= 0;
	}

	default FluidLike kgy$copy(int amount) {
		return this;
	}
}
