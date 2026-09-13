package dev.uncandango.kubegroovy.recipe.special;

import dev.uncandango.kubegroovy.plugin.builtin.event.ServerEvents;
import dev.uncandango.kubegroovy.recipe.ModifyCraftingItemKubeEvent;
import dev.uncandango.kubegroovy.recipe.ingredientaction.IngredientAction;
import dev.uncandango.kubegroovy.recipe.ingredientaction.IngredientActionHolder;
import dev.uncandango.kubegroovy.script.ScriptType;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;

import java.util.List;

public interface KubeGroovyCraftingRecipe extends CraftingRecipe {
	String MIRROR_KEY = "kubegroovy:mirror";
	String INGREDIENT_ACTIONS_KEY = "kubegroovy:ingredient_actions";
	String MODIFY_RESULT_KEY = "kubegroovy:modify_result";

	List<IngredientActionHolder> kgy$getIngredientActions();

	String kgy$getModifyResult();

	default NonNullList<ItemStack> kgy$getRemainingItems(CraftingInput input) {
		var list = NonNullList.withSize(input.size(), ItemStack.EMPTY);

		for (var i = 0; i < list.size(); i++) {
			list.set(i, IngredientAction.getRemaining(input, i, kgy$getIngredientActions()));
		}

		return list;
	}

	default ItemStack kgy$assemble(CraftingInput input, ItemStack baseResult) {
		var modifyResult = kgy$getModifyResult();
		//noinspection ConstantValue
		var result = (baseResult == null || baseResult.isEmpty()) ? ItemStack.EMPTY : baseResult.copy();

		if (!modifyResult.isEmpty()) {
			var event = new ModifyCraftingItemKubeEvent(input, result, 0);
			return (ItemStack) ServerEvents.MODIFY_RECIPE_RESULT.post(ScriptType.SERVER, modifyResult, event).value();
		}

		return result;
	}
}
