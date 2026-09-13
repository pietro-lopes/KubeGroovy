package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.SizedFluidIngredientKGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = SizedFluidIngredient.class, remap = false)
@RemapPrefixForGY("kgy$")
public abstract class SizedFluidIngredientMixin implements SizedFluidIngredientKGY {
}
