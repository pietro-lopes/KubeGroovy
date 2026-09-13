package dev.uncandango.kubegroovy.net;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.util.NotificationToastData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record NotificationPayload(NotificationToastData data) implements CustomPacketPayload {
	public static final StreamCodec<RegistryFriendlyByteBuf, NotificationPayload> STREAM_CODEC = NotificationToastData.STREAM_CODEC.map(NotificationPayload::new, NotificationPayload::data);

	@Override
	public Type<?> type() {
		return KubeGroovyNet.NOTIFICATION;
	}

	public void handle(IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			var p0 = KubeGroovy.PROXY.getClientPlayer();

			if (p0 != null) {
				p0.kgy$notify(data);
			}
		});
	}
}