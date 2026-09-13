package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.SizedIngredientKGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SizedIngredient.class)
@RemapPrefixForGY("kgy$")
public abstract class SizedIngredientMixin implements SizedIngredientKGY {
}
