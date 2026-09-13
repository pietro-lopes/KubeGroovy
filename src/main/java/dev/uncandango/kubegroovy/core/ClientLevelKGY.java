package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.client.KubeAnimatedParticle;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;

@RemapPrefixForGY("kgy$")
public interface ClientLevelKGY extends LevelKGY {
	@Override
	default ClientLevel kgy$self() {
		return (ClientLevel) this;
	}

	default KubeAnimatedParticle kubeParticle(double x, double y, double z, SpriteSet spriteSet) {
		return new KubeAnimatedParticle(kgy$self(), x, y, z, spriteSet);
	}
}
