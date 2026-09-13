package dev.uncandango.kubegroovy.recipe.schema.postprocessing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugin;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugins;
import dev.uncandango.kubegroovy.util.Lazy;
import net.minecraft.resources.Identifier;

import java.util.Map;

public record RecipePostProcessorType<T extends RecipePostProcessor>(Identifier id, MapCodec<T> mapCodec) {
	public static final Lazy<Map<Identifier, RecipePostProcessorType<?>>> MAP = Lazy.map(map -> KubeGroovyPlugins.forEachPlugin(type -> map.put(type.id, type), KubeGroovyPlugin::registerRecipePostProcessors));

	public static final Codec<RecipePostProcessorType<?>> CODEC = Identifier.CODEC.comapFlatMap(id -> {
		var type = MAP.get().get(id);

		if (type != null) {
			return DataResult.success(type);
		} else {
			return DataResult.error(() -> "Recipe post-processor type not found: " + id);
		}
	}, RecipePostProcessorType::id);
}
