package dev.uncandango.kubegroovy.recipe.ingredientaction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import dev.uncandango.kubegroovy.codec.KubeGroovyCodecs;
import dev.uncandango.kubegroovy.codec.KubeGroovyStreamCodecs;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugin;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugins;
import dev.uncandango.kubegroovy.util.Lazy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.Map;

public record IngredientActionType<T extends IngredientAction>(Identifier id, MapCodec<T> codec, StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
	public static final Lazy<Map<Identifier, IngredientActionType<?>>> TYPES = Lazy.map(map -> KubeGroovyPlugins.forEachPlugin(type -> map.put(type.id, type), KubeGroovyPlugin::registerIngredientActionTypes));

	public static final Codec<IngredientActionType<?>> CODEC = KubeGroovyCodecs.KubeGroovy_ID.comapFlatMap(id -> {
		var type = TYPES.get().get(id);

		if (type != null) {
			return DataResult.success(type);
		} else {
			return DataResult.error(() -> "Unknown ingredient action type: " + id);
		}
	}, IngredientActionType::id);

	public static final StreamCodec<RegistryFriendlyByteBuf, IngredientActionType<?>> STREAM_CODEC = KubeGroovyStreamCodecs.KubeGroovy_ID.map(s -> TYPES.get().get(s), IngredientActionType::id);

	public IngredientActionType(Identifier id, MapCodec<T> codec) {
		this(id, codec, ByteBufCodecs.fromCodecWithRegistries(codec.codec()));
	}
}
