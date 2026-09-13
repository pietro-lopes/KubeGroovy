package dev.uncandango.kubegroovy.core;

import com.mojang.serialization.Codec;
import dev.uncandango.kubegroovy.error.KubeRuntimeException;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.recipe.match.FluidMatch;
import dev.uncandango.kubegroovy.util.WithCodec;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.Objects;
import java.util.function.Predicate;

@RemapPrefixForGY("kgy$")
public interface FluidIngredientKGY extends WithCodec, FluidMatch, Predicate<FluidStack> {
	default FluidIngredient kgy$self() {
		throw new NoMixinException();
	}

	@Override
	default Codec<?> getCodec(Context cx) {
		return FluidIngredient.CODEC;
	}

	default SizedFluidIngredient kgy$withAmount(int amount) {
		return new SizedFluidIngredient(kgy$self(), amount);
	}

	@Override
	default boolean matches(RecipeMatchContext cx, FluidStack fluid, boolean exact) {
		if (fluid.isEmpty()) {
			return false;
		} else if (exact) {
			var fluids = kgy$self().fluids();
			return fluids.size() == 1 && fluid.is(fluids.getFirst());
		} else {
			return test(fluid);
		}
	}

	@Override
	default boolean matches(RecipeMatchContext cx, FluidIngredient in, boolean exact) {
		if (exact) {
			return Objects.equals(kgy$self(), in);
		}

		try {
			var fluids = kgy$self().fluids();

			if (fluids.isEmpty()) {
				return false;
			}

			int probeAmount = FluidType.BUCKET_VOLUME;
			for (var holder : fluids) {
				if (in.test(new FluidStack(holder, probeAmount))) {
					return true;
				}
			}
		} catch (Exception ex) {
			throw new KubeRuntimeException("Failed to test fluid ingredient " + in, ex);
		}
		return false;
	}

	@Override
	default boolean matches(RecipeMatchContext cx, SizedFluidIngredient in, boolean exact) {
		return matches(cx, in.ingredient(), exact);
	}
}
