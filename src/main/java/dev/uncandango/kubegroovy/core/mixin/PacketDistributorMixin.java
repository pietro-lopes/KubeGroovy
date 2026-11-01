package dev.uncandango.kubegroovy.core.mixin;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Game-test mock players have no negotiated payload channels, so any mod sending them a custom
/// payload would throw. Drop those sends instead, as game tests may also run in production.
@Mixin(PacketDistributor.class)
public abstract class PacketDistributorMixin {
	@Unique
	private static final String kgy$MOCK_PLAYER_NAME = "test-mock-player";

	@Inject(method = "sendToPlayer", at = @At("HEAD"), cancellable = true, remap = false)
	private static void kgy$sendToPlayer(ServerPlayer player, CustomPacketPayload payload, CustomPacketPayload[] payloads, CallbackInfo ci) {
		if (kgy$MOCK_PLAYER_NAME.equals(player.getGameProfile().name())) {
			ci.cancel();
		}
	}
}
