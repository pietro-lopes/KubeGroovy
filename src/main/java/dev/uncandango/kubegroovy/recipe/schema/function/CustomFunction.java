package dev.uncandango.kubegroovy.recipe.schema.function;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugins;
import dev.uncandango.kubegroovy.recipe.schema.RecipeSchema;
import dev.uncandango.kubegroovy.util.Lazy;
import net.minecraft.resources.Identifier;

import java.util.Map;

public record CustomFunction(Identifier id) implements RecipeSchemaFunction {
	public static final RecipeSchemaFunctionType<CustomFunction> TYPE = new RecipeSchemaFunctionType<>("custom", RecordCodecBuilder.mapCodec(instance -> instance.group(
		Identifier.CODEC.fieldOf("id").forGetter(CustomFunction::id)
	).apply(instance, CustomFunction::new)));

	public static final Lazy<Map<Identifier, ResolvedRecipeSchemaFunction>> MAP = Lazy.map(map -> KubeGroovyPlugins.forEachPlugin(p -> p.registerCustomRecipeSchemaFunctions(map::put)));

	@Override
	public RecipeSchemaFunctionType<?> type() {
		return TYPE;
	}

	@Override
	public DataResult<ResolvedRecipeSchemaFunction> resolve(DynamicOps<JsonElement> jsonOps, RecipeSchema schema) {
		var func = MAP.get().get(id);

		if (func == null) {
			return DataResult.error(() -> "Function '" + id + "' not registered");
		}

		return DataResult.success(func);
	}
}