package dev.uncandango.kubegroovy.recipe.ingredientaction;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.uncandango.kubegroovy.KubeGroovy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

public record ReplaceAction(ItemStack item) implements IngredientAction {
	public static final IngredientActionType<ReplaceAction> TYPE = new IngredientActionType<>(KubeGroovy.id("replace"), RecordCodecBuilder.mapCodec(instance -> instance.group(
		ItemStack.CODEC.fieldOf("item").forGetter(ReplaceAction::item)
	).apply(instance, ReplaceAction::new)), ItemStack.STREAM_CODEC.map(ReplaceAction::new, ReplaceAction::item));

	@Override
	public IngredientActionType<?> getType() {
		return TYPE;
	}

	@Override
	public ItemStack transform(ItemStack old, int index, CraftingInput input) {
		return item.copy();
	}
}
