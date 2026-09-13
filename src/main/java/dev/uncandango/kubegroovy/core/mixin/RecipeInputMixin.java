package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.RecipeInputKGY;
import net.minecraft.world.item.crafting.RecipeInput;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RecipeInput.class)
public interface RecipeInputMixin extends RecipeInputKGY {
}
