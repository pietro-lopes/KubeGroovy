package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.FluidIngredientKGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = FluidIngredient.class, remap = false)
@RemapPrefixForGY("kgy$")
public abstract class FluidIngredientMixin implements FluidIngredientKGY {
	@Override
	public FluidIngredient kgy$self() {
		return (FluidIngredient) (Object) this;
	}
}
