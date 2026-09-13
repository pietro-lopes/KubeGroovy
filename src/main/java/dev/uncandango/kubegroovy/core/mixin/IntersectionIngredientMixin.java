package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.CustomIngredientKGY;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.IntersectionIngredient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(IntersectionIngredient.class)
public abstract class IntersectionIngredientMixin implements CustomIngredientKGY {
	@Shadow
	@Final
	private List<Ingredient> children;

	@Override
	public boolean kgy$canBeUsedForMatching() {
		for (var child : children) {
			if (!child.kgy$canBeUsedForMatching()) {
				return false;
			}
		}

		return true;
	}
}
