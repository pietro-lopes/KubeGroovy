package dev.uncandango.kubegroovy.entity;

import dev.uncandango.kubegroovy.level.KubeLevelEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

public interface KubeEntityEvent extends KubeLevelEvent {
	@NullUnmarked
	Entity getEntity();

	@Nullable
	default Player getPlayer() {
		return getEntity() instanceof Player p ? p : null;
	}

	@Override
	default Level getLevel() {
		return getEntity().level();
	}
}