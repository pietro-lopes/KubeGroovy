package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.recipe.RecipeScriptContext;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.recipe.match.ReplacementMatchInfo;
import dev.uncandango.kubegroovy.recipe.schema.RecipeSchema;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeSerializer;

@RemapPrefixForGY("kgy$")
public interface RecipeLikeKGY {
	String kgy$getGroup();

	void kgy$setGroup(String group);

	Identifier kgy$getOrCreateId();

	RecipeSchema kgy$getSchema(Context cx);

	default String kgy$getMod() {
		return kgy$getOrCreateId().getNamespace();
	}

	ResourceKey<RecipeSerializer<?>> kgy$getTypeKey();

	default Identifier kgy$getType() {
		return kgy$getTypeKey().identifier();
	}

	RecipeSerializer<?> kgy$getSerializer();

	default boolean hasInput(RecipeMatchContext cx, ReplacementMatchInfo match) {
		return false;
	}

	default boolean replaceInput(RecipeScriptContext cx, ReplacementMatchInfo match, Object with) {
		return false;
	}

	default boolean hasOutput(RecipeMatchContext cx, ReplacementMatchInfo match) {
		return false;
	}

	default boolean replaceOutput(RecipeScriptContext cx, ReplacementMatchInfo match, Object with) {
		return false;
	}
}
