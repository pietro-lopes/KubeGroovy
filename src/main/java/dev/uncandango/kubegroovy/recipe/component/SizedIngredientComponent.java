package dev.uncandango.kubegroovy.recipe.component;

import com.mojang.serialization.Codec;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.IngredientWrapper;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.SizedIngredientWrapper;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.recipe.match.ItemMatch;
import dev.uncandango.kubegroovy.recipe.match.ReplacementMatchInfo;
import dev.uncandango.kubegroovy.script.bridge.TypeInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record SizedIngredientComponent(ResourceKey<RecipeComponentType<?>> type, Codec<SizedIngredient> codec, boolean allowEmpty) implements RecipeComponent<SizedIngredient> {
	public static final SizedIngredientComponent SIZED_INGREDIENT = new SizedIngredientComponent(
		RecipeComponentType.builtin("sized_ingredient"),
		SizedIngredient.NESTED_CODEC, false
	);
	public static final SizedIngredientComponent OPTIONAL_SIZED_INGREDIENT = new SizedIngredientComponent(
		RecipeComponentType.builtin("optional_sized_ingredient"),
		SizedIngredient.NESTED_CODEC, true
	);

	@Override
	public TypeInfo typeInfo() {
		return SizedIngredientWrapper.TYPE_INFO;
	}

	@Override
	public boolean hasPriority(RecipeMatchContext cx, @Nullable Object from) {
		return IngredientWrapper.isIngredientLike(from);
	}

	@Override
	public boolean matches(RecipeMatchContext cx, SizedIngredient value, ReplacementMatchInfo match) {
		return match.match() instanceof ItemMatch m && m.matches(cx, value.ingredient(), match.exact());
	}

	@Override
	public boolean isEmpty(SizedIngredient value) {
		return value.count() <= 0 || value.ingredient().isEmpty();
	}

	@Override
	public void buildUniqueId(UniqueIdBuilder builder, SizedIngredient value) {
		var tag = IngredientWrapper.tagKeyOf(value.ingredient());

		if (tag != null) {
			builder.append(tag.location());
		} else {
			var first = value.ingredient().kgy$getFirst();

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
	public List<Ingredient> spread(SizedIngredient value) {
		int count = value.count();

		if (count <= 0) {
			return List.of();
		} else if (count == 1) {
			return List.of(value.ingredient());
		} else {
			var list = new ArrayList<Ingredient>(count);
			var in = value.ingredient();

			for (int i = 0; i < count; i++) {
				list.add(in);
			}

			return list;
		}
	}
}
