package dev.uncandango.kubegroovy.recipe.schema;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.recipe.KubeRecipe;
import dev.uncandango.kubegroovy.recipe.RecipeTypeFunction;
import dev.uncandango.kubegroovy.script.SourceLine;
import dev.uncandango.kubegroovy.script.bridge.TypeInfo;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

public record KubeRecipeFactory(Identifier id, TypeInfo recipeType, Supplier<? extends KubeRecipe> factory) {
	public static final KubeRecipeFactory DEFAULT = new KubeRecipeFactory(KubeGroovy.id("basic"), TypeInfo.of(KubeRecipe.class), KubeRecipe::new);

	public KubeRecipeFactory(Identifier id, Class<?> recipeType, Supplier<? extends KubeRecipe> factory) {
		this(id, TypeInfo.of(recipeType), factory);
	}

	public KubeRecipe create(RecipeTypeFunction type, SourceLine sourceLine, boolean save) {
		var r = factory.get();
		r.sourceLine = sourceLine;
		r.type = type;
		r.initValues(save);
		return r;
	}
}
