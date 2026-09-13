package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.util.SlotFilter;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import java.util.ArrayList;
import java.util.List;

@RemapPrefixForGY("kgy$")
public interface RecipeInputKGY {
	default RecipeInput kgy$self() {
		return (RecipeInput) this;
	}

	default List<ItemStack> kgy$findAll(SlotFilter filter) {
		var list = new ArrayList<ItemStack>();
		int size = kgy$self().size();

		for (int i = 0; i < size; i++) {
			var stack = kgy$self().getItem(i);

			if (filter.checkFilter(i, stack)) {
				list.add(stack.copy());
			}
		}

		return list;
	}

	default List<ItemStack> kgy$findAll() {
		return kgy$findAll(SlotFilter.EMPTY);
	}

	default ItemStack find(SlotFilter filter, int skip) {
		for (var item : kgy$findAll(filter)) {
			if (skip == 0) {
				return item;
			}

			skip--;
		}

		return ItemStack.EMPTY;
	}

	default ItemStack find(SlotFilter filter) {
		return find(filter, 0);
	}
}
