package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.item.ItemPredicate;
import dev.uncandango.kubegroovy.item.ItemStackSet;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.ItemWrapper;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.ApiStatus;

import java.util.stream.Stream;

public interface CustomIngredientKGY extends ItemPredicate {
	Ingredient toVanilla();

	Stream<Holder<Item>> getItems();

	default boolean kgy$canBeUsedForMatching() {
		return false;
	}

	@ApiStatus.NonExtendable
	default Ingredient kgy$asIngredient() {
		return this.toVanilla();
	}

	@ApiStatus.NonExtendable
	default ItemStack[] kgy$getStackArray() {
		return getItems()
			.map(ItemStackTemplate::new)
			.map(ItemStackTemplate::create)
			.toArray(ItemStack[]::new);
	}

	default ItemStackSet kgy$getDisplayStacks() {
		var set = new ItemStackSet();

		for (var template : ItemWrapper.getList()) {
			var stack = template.create();
			if (test(stack)) {
				set.add(stack);
			}
		}

		return set;
	}
}
