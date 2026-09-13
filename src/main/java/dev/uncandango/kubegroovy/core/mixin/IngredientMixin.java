package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.IngredientKGY;
import dev.uncandango.kubegroovy.item.ItemPredicate;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.stream.Stream;

@Mixin(Ingredient.class)
@RemapPrefixForGY("kgy$")
public abstract class IngredientMixin implements IngredientKGY {
	@Mutable
	@Shadow
	public HolderSet<Item> values;

	@Override
	public Ingredient kgy$self() {
		return (Ingredient) (Object) this;
	}

	@Shadow
	@HideFromGY
	public abstract Stream<Holder<Item>> items();

	@Shadow
	public abstract ICustomIngredient getCustomIngredient();

	@Shadow
	public abstract boolean isCustom();

	@Override
	public boolean kgy$canBeUsedForMatching() {
		return !isCustom() || ((ItemPredicate) getCustomIngredient()).kgy$canBeUsedForMatching();
	}
}
