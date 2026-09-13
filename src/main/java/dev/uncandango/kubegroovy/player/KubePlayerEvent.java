package dev.uncandango.kubegroovy.player;

import dev.uncandango.kubegroovy.entity.KubeLivingEntityEvent;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

public interface KubePlayerEvent extends KubeLivingEntityEvent {
	@Override
	Player getEntity();

	@Override
	@Nullable
	default Player getPlayer() {
		return getEntity();
	}
}