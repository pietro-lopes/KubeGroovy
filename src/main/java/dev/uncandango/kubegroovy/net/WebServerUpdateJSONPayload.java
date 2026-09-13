package dev.uncandango.kubegroovy.net;

import com.google.gson.JsonElement;
import dev.uncandango.kubegroovy.codec.KubeGroovyStreamCodecs;
import dev.uncandango.kubegroovy.web.local.KubeGroovyWeb;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

public record WebServerUpdateJSONPayload(String event, String requiredTag, @Nullable JsonElement payload) implements CustomPacketPayload {
	public static final StreamCodec<ByteBuf, WebServerUpdateJSONPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, WebServerUpdateJSONPayload::event,
		ByteBufCodecs.STRING_UTF8, WebServerUpdateJSONPayload::requiredTag,
		KubeGroovyStreamCodecs.JSON_ELEMENT, WebServerUpdateJSONPayload::payload,
		WebServerUpdateJSONPayload::new
	);

	@Override
	public Type<?> type() {
		return KubeGroovyNet.WEB_SERVER_JSON_UPDATE;
	}

	public void handle(IPayloadContext ctx) {
		KubeGroovyWeb.broadcastUpdate("server/" + event, requiredTag, () -> payload);
	}
}