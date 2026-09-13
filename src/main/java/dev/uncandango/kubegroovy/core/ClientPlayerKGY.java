package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.player.PlayerStatsGY;
import dev.uncandango.kubegroovy.typings.Info;
import dev.uncandango.kubegroovy.typings.ThisIs;
import dev.uncandango.kubegroovy.util.NotificationToastData;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

@RemapPrefixForGY("kgy$")
public interface ClientPlayerKGY extends PlayerKGY {
	@Override
	@HideFromGY
	default AbstractClientPlayer kgy$self() {
		return (AbstractClientPlayer) this;
	}

	@ThisIs(AbstractClientPlayer.class)
	@Info("Checks if the entity is a client-side player.")
	default boolean kgy$isClientPlayer() {
		return true;
	}

	@Override
	default void kgy$sendData(String channel, @Nullable CompoundTag data) {
	}

	@Override
	default PlayerStatsGY kgy$getStats() {
		throw new IllegalStateException("Can't access other client player stats!");
	}

	@Override
	default boolean kgy$isMiningBlock() {
		return false;
	}

	@Override
	default void kgy$notify(NotificationToastData notification) {
	}
}
