package dev.uncandango.kubegroovy.net;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.script.ScriptType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DisplayClientErrorsPayload() implements CustomPacketPayload {
	public static final StreamCodec<ByteBuf, DisplayClientErrorsPayload> STREAM_CODEC = StreamCodec.unit(new DisplayClientErrorsPayload());

	@Override
	public Type<?> type() {
		return KubeGroovyNet.DISPLAY_CLIENT_ERRORS;
	}

	public void handle(IPayloadContext ctx) {
		ctx.enqueueWork(() -> KubeGroovy.PROXY.openErrors(ScriptType.CLIENT));
	}
}