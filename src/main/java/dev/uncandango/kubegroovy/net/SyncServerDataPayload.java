package dev.uncandango.kubegroovy.net;

import dev.uncandango.kubegroovy.KubeGroovy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncServerDataPayload(KubeServerData data) implements CustomPacketPayload {
	public static final StreamCodec<RegistryFriendlyByteBuf, SyncServerDataPayload> STREAM_CODEC = KubeServerData.STREAM_CODEC.map(SyncServerDataPayload::new, SyncServerDataPayload::data);

	@Override
	public Type<?> type() {
		return KubeGroovyNet.SYNC_SERVER_DATA;
	}

	public void handle(IPayloadContext ctx) {
		ctx.enqueueWork(() -> KubeGroovy.PROXY.updateServerData(data));
	}
}