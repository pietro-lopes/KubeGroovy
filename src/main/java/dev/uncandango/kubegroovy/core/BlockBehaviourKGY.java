package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.block.callback.RandomTickCallback;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.world.level.block.SoundType;

import java.util.function.Consumer;

@RemapPrefixForGY("kgy$")
public interface BlockBehaviourKGY extends BlockProviderKGY {
	default void kgy$setHasCollision(boolean v) {
		throw new NoMixinException();
	}

	default void kgy$setExplosionResistance(float v) {
		throw new NoMixinException();
	}

	default void kgy$setIsRandomlyTicking(boolean v) {
		throw new NoMixinException();
	}

	default void kgy$setRandomTickCallback(Consumer<RandomTickCallback> callback) {
		throw new NoMixinException();
	}

	default void kgy$setSoundType(SoundType v) {
		throw new NoMixinException();
	}

	default void kgy$setFriction(float v) {
		throw new NoMixinException();
	}

	default void kgy$setSpeedFactor(float v) {
		throw new NoMixinException();
	}

	default void kgy$setJumpFactor(float v) {
		throw new NoMixinException();
	}

	default void kgy$setNameKey(String key) {
		throw new NoMixinException();
	}
}
