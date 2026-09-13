package dev.uncandango.kubegroovy.net;

import dev.uncandango.kubegroovy.KubeGroovy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ReloadStartupScriptsPayload(boolean dedicated) implements CustomPacketPayload {
	public static final StreamCodec<ByteBuf, ReloadStartupScriptsPayload> STREAM_CODEC = ByteBufCodecs.BOOL.map(ReloadStartupScriptsPayload::new, ReloadStartupScriptsPayload::dedicated);

	@Override
	public Type<?> type() {
		return KubeGroovyNet.RELOAD_STARTUP_SCRIPTS;
	}

	public void handle(IPayloadContext ctx) {
		ctx.enqueueWork(() -> KubeGroovy.PROXY.reloadStartupScripts(dedicated));
	}
}