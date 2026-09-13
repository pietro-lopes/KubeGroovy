package dev.uncandango.kubegroovy.net;

import com.mojang.serialization.JsonOps;
import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.web.local.KubeGroovyWeb;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

public record WebServerUpdateNBTPayload(String event, String requiredTag, Optional<Tag> payload) implements CustomPacketPayload {
	public static final StreamCodec<ByteBuf, WebServerUpdateNBTPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, WebServerUpdateNBTPayload::event,
		ByteBufCodecs.STRING_UTF8, WebServerUpdateNBTPayload::requiredTag,
		ByteBufCodecs.optional(ByteBufCodecs.TAG), WebServerUpdateNBTPayload::payload,
		WebServerUpdateNBTPayload::new
	);

	@Override
	public Type<?> type() {
		return KubeGroovyNet.WEB_SERVER_NBT_UPDATE;
	}

	public void handle(IPayloadContext ctx) {
		int count = KubeGroovyWeb.broadcastUpdate("server/" + event, requiredTag, () -> payload.map(tag -> NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag)).orElse(null));

		if (count == 0 && event.equals("highlight/items")) {
			var list = ((CompoundTag) payload.orElseThrow()).getList("items").orElse(new ListTag());

			for (var e : list) {
				if (!(e instanceof CompoundTag t)) {
					continue;
				}

				KubeGroovy.LOGGER.info("[Highlighted Item] {}", t.getStringOr("string", ""));

				t.getList("tags").ifPresent(l -> {
					for (var tag : l) {
						tag.asString().ifPresent(s -> KubeGroovy.LOGGER.info("[Highlighted Item] - #{}", s));
					}
				});
			}
		}
	}
}