package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.CustomIngredientKGY;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.DifferenceIngredient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DifferenceIngredient.class)
public abstract class DifferenceIngredientMixin implements CustomIngredientKGY {
	@Shadow(remap = false)
	@Final
	private Ingredient base;

	@Shadow(remap = false)
	@Final
	private Ingredient subtracted;

	@Override
	public boolean kgy$canBeUsedForMatching() {
		return base.kgy$canBeUsedForMatching() && subtracted.kgy$canBeUsedForMatching();
	}
}
