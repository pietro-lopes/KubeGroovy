package dev.uncandango.kubegroovy.net;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.script.ConsoleLine;
import dev.uncandango.kubegroovy.script.ScriptType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record DisplayServerErrorsPayload(int scriptType, List<ConsoleLine> errors, List<ConsoleLine> warnings) implements CustomPacketPayload {
	public static final StreamCodec<FriendlyByteBuf, DisplayServerErrorsPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, DisplayServerErrorsPayload::scriptType,
		ByteBufCodecs.collection(ArrayList::new, ConsoleLine.STREAM_CODEC), DisplayServerErrorsPayload::errors,
		ByteBufCodecs.collection(ArrayList::new, ConsoleLine.STREAM_CODEC), DisplayServerErrorsPayload::warnings,
		DisplayServerErrorsPayload::new
	);

	@Override
	public Type<?> type() {
		return KubeGroovyNet.DISPLAY_SERVER_ERRORS;
	}

	public void handle(IPayloadContext ctx) {
		ctx.enqueueWork(() -> KubeGroovy.PROXY.openErrors(ScriptType.values()[scriptType], errors, warnings));
	}
}