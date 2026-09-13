package dev.uncandango.kubegroovy.client.icon;

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

public record KubeIconType<T extends KubeIcon>(Identifier id, MapCodec<T> codec, StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
	public static final Lazy<Map<Identifier, KubeIconType<?>>> TYPES = Lazy.map(map -> KubeGroovyPlugins.forEachPlugin(type -> map.put(type.id, type), KubeGroovyPlugin::registerIconTypes));

	public static final Codec<KubeIconType<?>> CODEC = KubeGroovyCodecs.KubeGroovy_ID.comapFlatMap(id -> {
		var type = TYPES.get().get(id);

		if (type != null) {
			return DataResult.success(type);
		} else {
			return DataResult.error(() -> "Unknown icon type: " + id);
		}
	}, KubeIconType::id);

	public static final StreamCodec<RegistryFriendlyByteBuf, KubeIconType<?>> STREAM_CODEC = KubeGroovyStreamCodecs.KubeGroovy_ID.map(s -> TYPES.get().get(s), KubeIconType::id);

	public KubeIconType(Identifier id, MapCodec<T> codec) {
		this(id, codec, ByteBufCodecs.fromCodecWithRegistries(codec.codec()));
	}
}
