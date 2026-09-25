package dev.uncandango.kubegroovy.item;

import dev.uncandango.kubegroovy.core.IngredientSupplierKGY;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.IngredientWrapper;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.ItemWrapper;
import dev.uncandango.kubegroovy.script.bridge.BaseFunction;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

// TODO: Rework to fit ingredient changes
@RemapPrefixForGY("kgy$")
public interface ItemPredicate extends Predicate<ItemStack>, IngredientSupplierKGY {
	TypeInfo TYPE_INFO = TypeInfo.of(ItemPredicate.class);
	ItemPredicate NONE = stack -> false;
	ItemPredicate ALL = stack -> true;

	static ItemPredicate wrap(@Nullable Object from) {
		var cx = Context.getCurrent();
		if (from == null) {
			return NONE;
		} else if (from instanceof BaseFunction func) {
			return (ItemPredicate) cx.createInterfaceAdapter(TYPE_INFO, func);
		} else {
			if (from instanceof CharSequence s) {
				if (s.equals("*")) {
					return ALL;
				} else if (s.isEmpty() || s.equals("-")) {
					return NONE;
				}
			}

			var in = IngredientWrapper.wrap(from);

			return in.kgy$isWildcard() ? ALL : in;
		}
	}

	// TODO: remove or rework
	@Deprecated(forRemoval = true)
	default ItemStack[] kgy$getStackArray() {
		return ItemWrapper.getList().stream().map(ItemStackTemplate::create).filter(this).toArray(ItemStack[]::new);
	}

	// TODO: remove or rework
	@Deprecated(forRemoval = true)
	default ItemStackSet kgy$getStacks() {
		return new ItemStackSet(kgy$getStackArray());
	}

	// TODO: remove or rework
	// specifically this can VERY likely just be removed since we can use SlotDisplay instead?
	@Deprecated(forRemoval = true)
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

	default boolean kgy$isWildcard() {
		return this == ALL;
	}

	default Set<Item> kgy$getItemTypes() {
		var items = kgy$getStackArray();

		if (items.length == 1 && !items[0].isEmpty()) {
			return Set.of(items[0].getItem());
		}

		var set = new LinkedHashSet<Item>(items.length);

		for (var stack : items) {
			if (!stack.isEmpty()) {
				set.add(stack.getItem());
			}
		}

		return set;
	}

	default Set<String> kgy$getItemIds() {
		var items = kgy$getStackArray();

		if (items.length == 1 && !items[0].isEmpty()) {
			return Set.of(items[0].kgy$getId());
		}

		var ids = new LinkedHashSet<String>(items.length);

		for (var item : items) {
			if (!item.isEmpty()) {
				ids.add(item.kgy$getId());
			}
		}

		return ids;
	}

	/// Marks whether an ingredient is safe to be used to match recipe filters during the recipe event.
	/// (The answer is usually no for non-Vanilla ingredients, but can be overridden manually by addons or downstream mods with integration.)
	default boolean kgy$canBeUsedForMatching() {
		return true;
	}

	@Override
	default Ingredient kgy$asIngredient() {
		return Ingredient.of(Arrays.stream(kgy$getStackArray()).map(ItemStack::getItem));
	}
}
