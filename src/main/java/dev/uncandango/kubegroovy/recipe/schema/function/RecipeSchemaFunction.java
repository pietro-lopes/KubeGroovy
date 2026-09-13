package dev.uncandango.kubegroovy.recipe.schema.function;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import dev.uncandango.kubegroovy.codec.KubeGroovyCodecs;
import dev.uncandango.kubegroovy.recipe.schema.RecipeSchema;

import java.util.List;

public interface RecipeSchemaFunction {
	Codec<RecipeSchemaFunction> DIRECT_CODEC = Codec.lazyInitialized(() -> RecipeSchemaFunctionType.CODEC.dispatch(RecipeSchemaFunction::type, RecipeSchemaFunctionType::mapCodec));
	Codec<RecipeSchemaFunction> LIST_CODEC = DIRECT_CODEC.listOf().flatXmap(list -> DataResult.success(new BundleFunction(list)), func -> func instanceof BundleFunction(List<RecipeSchemaFunction> functions) ? DataResult.success(functions) : DataResult.error(() -> "Not a list"));

	Codec<RecipeSchemaFunction> CODEC = KubeGroovyCodecs.or(DIRECT_CODEC, LIST_CODEC);

	RecipeSchemaFunctionType<?> type();

	DataResult<ResolvedRecipeSchemaFunction> resolve(DynamicOps<JsonElement> jsonOps, RecipeSchema schema);
}
