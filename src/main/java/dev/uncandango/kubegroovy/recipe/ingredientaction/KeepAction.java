package dev.uncandango.kubegroovy.recipe.ingredientaction;

import com.mojang.serialization.MapCodec;
import dev.uncandango.kubegroovy.KubeGroovy;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

public class KeepAction implements IngredientAction {
	public static final KeepAction INSTANCE = new KeepAction();
	public static final IngredientActionType<KeepAction> TYPE = new IngredientActionType<>(KubeGroovy.id("keep"), MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));

	@Override
	public IngredientActionType<?> getType() {
		return TYPE;
	}

	@Override
	public ItemStack transform(ItemStack old, int index, CraftingInput input) {
		return old.copyWithCount(1);
	}
}
