package dev.uncandango.kubegroovy.recipe.component;

import com.mojang.serialization.Codec;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.IngredientWrapper;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.recipe.match.ItemMatch;
import dev.uncandango.kubegroovy.recipe.match.ReplacementMatchInfo;
import dev.uncandango.kubegroovy.util.OpsContainer;
import dev.uncandango.kubegroovy.script.bridge.TypeInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.jspecify.annotations.Nullable;

public record IngredientComponent(ResourceKey<RecipeComponentType<?>> type, Codec<Ingredient> codec, boolean allowEmpty) implements RecipeComponent<Ingredient> {
	public static final IngredientComponent INGREDIENT = new IngredientComponent(
		RecipeComponentType.builtin("ingredient"),
		Ingredient.CODEC, false
	);
	public static final IngredientComponent OPTIONAL_INGREDIENT = new IngredientComponent(
		RecipeComponentType.builtin("optional_ingredient"),
		Ingredient.CODEC, true
	);

	@Override
	public TypeInfo typeInfo() {
		return IngredientWrapper.TYPE_INFO;
	}

	@Override
	public boolean hasPriority(RecipeMatchContext cx, @Nullable Object from) {
		return IngredientWrapper.isIngredientLike(from);
	}

	@Override
	public boolean matches(RecipeMatchContext cx, Ingredient value, ReplacementMatchInfo match) {
		return match.match() instanceof ItemMatch m && !value.isEmpty() && m.matches(cx, value, match.exact());
	}

	@Override
	public boolean isEmpty(Ingredient value) {
		if (value.isEmpty()) {
			return true;
		}

		if (value.isCustom()) {
			return value.items().noneMatch(holder -> {
				var item = holder.value();
				return !item.getDefaultInstance().isEmpty() && item.asItem() != Items.BARRIER;
			});
		}

		var stacks = value.getValues();
		if (stacks.size() == 0) {
			return true;
		}

		int count = 0;
		for (var stack : stacks) {
			if (!stack.value().getDefaultInstance().isEmpty() && stack.value().asItem() != Items.BARRIER) {
				count++;
			}
		}
		return count == 0;
	}

	@Override
	public void buildUniqueId(UniqueIdBuilder builder, Ingredient value) {
		var tag = IngredientWrapper.tagKeyOf(value);

		if (tag != null) {
			builder.append(tag.location());
		} else {
			var first = value.kgy$getFirst();

			if (!first.isEmpty()) {
				builder.append(first.kgy$getIdLocation());
			}
		}
	}

	@Override
	public String toString() {
		return type.toString();
	}

	@Override
	public String toString(OpsContainer ops, Ingredient value) {
		return value.kgy$toIngredientString(ops.nbt());
	}
}
