package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.recipe.RecipeScriptContext;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.recipe.match.ItemMatch;
import dev.uncandango.kubegroovy.recipe.match.ReplacementMatchInfo;
import dev.uncandango.kubegroovy.recipe.schema.RecipeSchema;
import dev.uncandango.kubegroovy.script.KubeGroovyContext;
import dev.uncandango.kubegroovy.server.ServerScriptManager;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

@RemapPrefixForGY("kgy$")
public interface RecipeHolderKGY extends RecipeLikeKGY {
	default RecipeHolder<?> kgy$self() {
		return (RecipeHolder<?>) this;
	}

	default Recipe<?> kgy$getRecipe() {
		return kgy$self().value();
	}

	@Override
	default String kgy$getGroup() {
		return kgy$getRecipe().group();
	}

	@Override
	default void kgy$setGroup(String group) {
	}

	@Override
	default Identifier kgy$getOrCreateId() {
		return kgy$self().id().identifier();
	}

	@Override
	default RecipeSerializer<?> kgy$getSerializer() {
		return kgy$getRecipe().getSerializer();
	}

	@Override
	default RecipeSchema kgy$getSchema(Context cx) {
		var s = kgy$getType();
		return ((ServerScriptManager) ((KubeGroovyContext) cx).kgyFactory.manager).recipeSchemaStorage.namespaces.get(s.getNamespace()).get(s.getPath()).schema;
	}

	@Override
	default ResourceKey<RecipeSerializer<?>> kgy$getTypeKey() {
		return BuiltInRegistries.RECIPE_SERIALIZER.getResourceKey(kgy$getSerializer()).orElseThrow();
	}

	@Override
	default boolean hasInput(RecipeMatchContext cx, ReplacementMatchInfo match) {
		if (match.match() instanceof ItemMatch m) {
			for (var ingredient : kgy$getRecipe().placementInfo().ingredients()) {
				if (!ingredient.isEmpty() && ingredient.kgy$canBeUsedForMatching() && m.matches(cx, ingredient, match.exact())) {
					return true;
				}
			}
		}

		return false;
	}

	@Override
	default boolean replaceInput(RecipeScriptContext cx, ReplacementMatchInfo match, Object with) {
		return false;
	}

	@Override
	default boolean hasOutput(RecipeMatchContext cx, ReplacementMatchInfo match) {
		if (match.match() instanceof ItemMatch m) {
			var displayContext = new ContextMap.Builder()
				.withOptionalParameter(SlotDisplayContext.REGISTRIES, cx.registries())
				.create(SlotDisplayContext.CONTEXT);

			var stream = kgy$getRecipe().display().stream()
				.flatMap(display -> display.result().resolveForStacks(displayContext).stream())
				.toList();
			for (var stack : stream) {
				if (!stack.isEmpty() && m.matches(cx, stack, match.exact())) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	default boolean replaceOutput(RecipeScriptContext cx, ReplacementMatchInfo match, Object with) {
		return false;
	}
}
