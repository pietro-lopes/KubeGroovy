package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.FluidStackKGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(FluidStack.class)
@RemapPrefixForGY("kgy$")
public abstract class FluidStackMixin implements FluidStackKGY {
}
