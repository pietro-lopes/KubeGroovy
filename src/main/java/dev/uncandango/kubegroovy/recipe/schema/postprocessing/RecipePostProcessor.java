package dev.uncandango.kubegroovy.recipe.schema.postprocessing;

import com.mojang.serialization.Codec;
import dev.uncandango.kubegroovy.recipe.KubeRecipe;
import dev.uncandango.kubegroovy.recipe.component.RecipeValidationContext;

public interface RecipePostProcessor {
	Codec<RecipePostProcessor> CODEC = RecipePostProcessorType.CODEC.dispatch("type", RecipePostProcessor::type, RecipePostProcessorType::mapCodec);

	RecipePostProcessorType<?> type();

	void process(RecipeValidationContext ctx, KubeRecipe recipe);
}
