package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;

@RemapPrefixForGY("kgy$")
public interface ServerLevelKGY extends LevelKGY, WithPersistentData {
	@Override
	default ServerLevel kgy$self() {
		return (ServerLevel) this;
	}

	@Override
	default void kgy$spawnParticles(ParticleOptions particle, boolean overrideLimiter, double x, double y, double z, double vx, double vy, double vz, int count, double speed) {
		for (var player : kgy$self().players()) {
			kgy$self().sendParticles(player, particle, overrideLimiter, false, x, y, z, count, vx, vy, vz, speed);
		}
	}
}
