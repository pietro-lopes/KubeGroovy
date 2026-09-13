package dev.uncandango.kubegroovy.recipe.ingredientaction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.plugin.builtin.event.ServerEvents;
import dev.uncandango.kubegroovy.recipe.ModifyCraftingItemKubeEvent;
import dev.uncandango.kubegroovy.script.ScriptType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

public record CustomIngredientAction(String id) implements IngredientAction {
	public static final IngredientActionType<CustomIngredientAction> TYPE = new IngredientActionType<>(KubeGroovy.id("custom"), RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.STRING.fieldOf("id").forGetter(CustomIngredientAction::id)
	).apply(instance, CustomIngredientAction::new)), ByteBufCodecs.STRING_UTF8.map(CustomIngredientAction::new, CustomIngredientAction::id));

	@Override
	public IngredientActionType<?> getType() {
		return TYPE;
	}

	@Override
	public ItemStack transform(ItemStack old, int index, CraftingInput input) {
		//noinspection DataFlowIssue
		return ((ItemStack) ServerEvents.MODIFY_RECIPE_INGREDIENT.post(ScriptType.SERVER, id, new ModifyCraftingItemKubeEvent(input, old, index)).value()).copy();
	}
}
