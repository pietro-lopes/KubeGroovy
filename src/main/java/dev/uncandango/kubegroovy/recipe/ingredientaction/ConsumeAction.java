package dev.uncandango.kubegroovy.recipe.ingredientaction;

import com.mojang.serialization.MapCodec;
import dev.uncandango.kubegroovy.KubeGroovy;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

public class ConsumeAction implements IngredientAction {
	public static final ConsumeAction INSTANCE = new ConsumeAction();
	public static final IngredientActionType<ConsumeAction> TYPE = new IngredientActionType<>(KubeGroovy.id("consume"), MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));

	@Override
	public IngredientActionType<?> getType() {
		return TYPE;
	}

	@Override
	public ItemStack transform(ItemStack old, int index, CraftingInput input) {
		return ItemStack.EMPTY;
	}
}
