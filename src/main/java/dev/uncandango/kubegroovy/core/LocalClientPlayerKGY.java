package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.client.KubeSessionData;
import dev.uncandango.kubegroovy.client.NotificationToast;
import dev.uncandango.kubegroovy.net.SendDataFromClientPayload;
import dev.uncandango.kubegroovy.player.PlayerStatsGY;
import dev.uncandango.kubegroovy.typings.Info;
import dev.uncandango.kubegroovy.typings.Param;
import dev.uncandango.kubegroovy.typings.ThisIs;
import dev.uncandango.kubegroovy.util.NotificationToastData;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

@RemapPrefixForGY("kgy$")
public interface LocalClientPlayerKGY extends ClientPlayerKGY {
	@Override
	@HideFromGY
	default LocalPlayer kgy$self() {
		return (LocalPlayer) this;
	}

	default Minecraft kgy$getMinecraft() {
		return Minecraft.getInstance();
	}

	@Override
	@Info(value = "Runs the specified console command client-side with the player's permission level.", params = {
		@Param(name = "command", value = "The console command. Slash at the beginning is optional."),
	})
	default void kgy$runCommand(String command) {
		kgy$self().connection.sendCommand(command);
	}

	@Override
	@Info(value = "Runs the specified console command client-side with the player's permission level. The command won't output any logs in chat nor console.", params = {
		@Param(name = "command", value = "The console command. Slash at the beginning is optional."),
	})
	default void kgy$runCommandSilent(String command) {
		kgy$self().connection.sendCommand(command);
	}

	@Override
	@ThisIs(classNames = "net.minecraft.client.player.LocalPlayer")
	@Info("Checks, whether the entity is a reference to yourself - that is - the client player you are controlling.")
	default boolean kgy$isSelf() {
		return true;
	}

	@Override
	default void kgy$sendData(String channel, @Nullable CompoundTag data) {
		if (!channel.isEmpty()) {
			ClientPacketDistributor.sendToServer(new SendDataFromClientPayload(channel, data));
		}
	}

	@Override
	default PlayerStatsGY kgy$getStats() {
		return new PlayerStatsGY(kgy$self(), kgy$self().getStats());
	}

	@Override
	@Info("Checks, whether the player is currently mining a block.")
	default boolean kgy$isMiningBlock() {
		return Minecraft.getInstance().gameMode.isDestroying();
	}

	@Override
	default void kgy$notify(NotificationToastData notification) {
		var mc = Minecraft.getInstance();
		mc.getToastManager().addToast(new NotificationToast(mc, notification));
	}

	@Override
	default void kgy$setActivePostShader(@Nullable Identifier id) {
		var sessionData = KubeSessionData.of(kgy$self().connection);

		if (sessionData != null) {
			sessionData.activePostShader = id;
			var mc = kgy$getMinecraft();
			mc.gameRenderer.checkEntityPostEffect(mc.options.getCameraType().isFirstPerson() ? mc.getCameraEntity() : null);
		}
	}
}
