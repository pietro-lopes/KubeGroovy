package dev.uncandango.kubegroovy.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import dev.uncandango.kubegroovy.error.KubeRuntimeException;
import dev.uncandango.kubegroovy.holder.KubeGroovyHolderSet;
import dev.uncandango.kubegroovy.ingredient.CreativeTabIngredient;
import dev.uncandango.kubegroovy.item.ItemPredicate;
import dev.uncandango.kubegroovy.item.ItemStackSet;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.IngredientWrapper;
import dev.uncandango.kubegroovy.recipe.RecipeScriptContext;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.recipe.match.ItemMatch;
import dev.uncandango.kubegroovy.recipe.match.Replaceable;
import dev.uncandango.kubegroovy.util.Cast;
import dev.uncandango.kubegroovy.util.ID;
import dev.uncandango.kubegroovy.util.WithCodec;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.common.crafting.DifferenceIngredient;
import net.neoforged.neoforge.common.crafting.IntersectionIngredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.registries.holdersets.AnyHolderSet;
import net.neoforged.neoforge.registries.holdersets.OrHolderSet;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@RemapPrefixForGY("kgy$")
public interface IngredientKGY extends ItemPredicate, Replaceable, WithCodec, ItemMatch {
	default Ingredient kgy$self() {
		throw new NoMixinException();
	}

	@Override
	default ItemStack[] kgy$getStackArray() {
		return kgy$self().items()
			.map(ItemStack::new)
			.toArray(ItemStack[]::new);
	}

	@Override
	default ItemStackSet kgy$getDisplayStacks() {
		var set = new ItemStackSet();

		kgy$self().display()
			.resolve(ContextMap.EMPTY, SlotDisplay.ItemStackContentsFactory.INSTANCE)
			.forEachOrdered(set::add);

		return set;
	}

	default Ingredient kgy$and(Ingredient ingredient) {
		return IntersectionIngredient.of(kgy$self(), ingredient);
	}

	default Ingredient kgy$or(Ingredient ingredient) {
		return CompoundIngredient.of(kgy$self(), ingredient);
	}

	default Ingredient kgy$except(Ingredient subtracted) {
		return DifferenceIngredient.of(kgy$self(), subtracted);
	}

	default SizedIngredient kgy$asStack() {
		return new SizedIngredient(kgy$self(), 1);
	}

	default SizedIngredient kgy$withCount(int count) {
		return new SizedIngredient(kgy$self(), count);
	}

	@Override
	default boolean kgy$isWildcard() {
		return kgy$self().values instanceof AnyHolderSet<?>;
	}

	default Ingredient kgy$asIngredient() {
		return kgy$self();
	}

	@Override
	default Codec<?> getCodec(Context cx) {
		return Ingredient.CODEC;
	}

	@Override
	default Object replaceThisWith(RecipeScriptContext cx, Object with) {
		var t = kgy$self();
		var r = IngredientWrapper.wrap(cx.cx(), with);

		if (!r.equals(t)) {
			return r;
		}

		return this;
	}

	@Override
	default boolean matches(RecipeMatchContext cx, ItemStack item, boolean exact) {
		if (item.isEmpty()) {
			return false;
		} else if (exact) {
			var display = kgy$self().display();
			// TODO: non-empty context map?
			var stacks = display.resolveForStacks(ContextMap.EMPTY);
			return stacks.size() == 1 && ItemStack.isSameItemSameComponents(stacks.getFirst(), item);
		} else {
			return test(item);
		}
	}

	@Override
	default boolean matches(RecipeMatchContext cx, Ingredient in, boolean exact) {
		if (exact) {
			return Objects.equals(kgy$self(), in);
		}

		try {
			var items = in.items()
				.map(ItemStack::new)
				.toArray(ItemStack[]::new);
			for (var stack : items) {
				if (test(stack)) {
					return true;
				}
			}
		} catch (Exception ex) {
			throw new KubeRuntimeException("Failed to test ingredient " + in, ex);
		}

		return false;
	}

	@Nullable
	default TagKey<Item> kgy$getTagKey() {
		return IngredientWrapper.tagKeyOf(kgy$self());
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static @Nullable String setToString(HolderSet<Item> in, DataComponentPatch components, @Nullable DynamicOps<Tag> ops) {
		Function<Holder<?>, String> holderToString = holder ->
			new ItemStack(Cast.to(holder), 1, components).kgy$toItemString0(ops);
		return switch (in) {
			case HolderSet.Direct direct -> {
				List<String> list = direct.stream().map(holderToString).toList();
				yield list.size() == 1 ? list.getFirst() : list.toString();
			}
			case HolderSet.Named<?> tag -> "#" + ID.reduce(tag.key().location());
			case OrHolderSet<?> or -> {
				List<String> children = new ArrayList<>();
				for (HolderSet child : or.getComponents()) {
					var result = setToString(child, components, ops);
					if (result == null) {
						yield null;
					}

					children.add(result);
				}
				yield children.size() == 1 ? children.getFirst() : children.toString();
			}
			case AnyHolderSet<?> any -> "*";
			case KubeGroovyHolderSet kgy -> kgy.kgy$toIngredientString(holderToString);
			default -> null;
		};
	}

	default ItemStack kgy$getFirst() {
		// TODO (again!): context?
		return kgy$self().display().resolveForFirstStack(ContextMap.EMPTY);
	}

	default String kgy$toIngredientString(@Nullable DynamicOps<Tag> ops) {
		var in = kgy$self();

		switch (in.getCustomIngredient()) {
			case DataComponentIngredient dci -> {
				var string = setToString(dci.itemSet(), dci.components(), ops);
				if (string != null) {
					return string;
				}
			}
			case null -> {
				var string = setToString(in.values, DataComponentPatch.EMPTY, ops);
				if (string != null) {
					return string;
				}
			}
			case CreativeTabIngredient(var tab) -> {
				return "%" + tab;
			}
			default -> {
			}
		}

		return Ingredient.CODEC.encodeStart(ops, in).getOrThrow().toString();
	}
}
