package dev.uncandango.kubegroovy.block;

import dev.uncandango.kubegroovy.block.callback.RandomTickCallback;
import dev.uncandango.kubegroovy.block.state.BlockStatePredicate;
import dev.uncandango.kubegroovy.event.KubeEvent;
import dev.uncandango.kubegroovy.typings.Info;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;

import java.util.function.Consumer;

public class BlockModificationKubeEvent implements KubeEvent {

	@Info("""
		Modifies blocks that match the given predicate.
		
		**NOTE**: tag predicates are not supported at this time.
		""")
	public void modify(BlockStatePredicate predicate, Consumer<BlockModifications> c) {
		for (var block : predicate.getBlocks()) {
			c.accept(new BlockModifications(block));
		}
	}

	public record BlockModifications(Block block) {
		public void setHasCollision(boolean v) {
			block.kgy$setHasCollision(v);
		}

		public void setExplosionResistance(float v) {
			block.kgy$setExplosionResistance(v);
		}

		public void setIsRandomlyTicking(boolean v) {
			block.kgy$setIsRandomlyTicking(v);
		}

		public void setRandomTickCallback(Consumer<RandomTickCallback> callback) {
			block.kgy$setRandomTickCallback(callback);
		}

		public void setSoundType(SoundType v) {
			block.kgy$setSoundType(v);
		}

		public void setFriction(float v) {
			block.kgy$setFriction(v);
		}

		public void setSpeedFactor(float v) {
			block.kgy$setSpeedFactor(v);
		}

		public void setJumpFactor(float v) {
			block.kgy$setJumpFactor(v);
		}

		public void setNameKey(String key) {
			block.kgy$setNameKey(key);
		}

		public void setDestroySpeed(float v) {
			for (var state : block.kgy$getBlockStates()) {
				state.kgy$setDestroySpeed(v);
			}
		}

		public void setLightEmission(int v) {
			for (var state : block.kgy$getBlockStates()) {
				state.kgy$setLightEmission(v);
			}
		}

		public void setRequiresTool(boolean v) {
			for (var state : block.kgy$getBlockStates()) {
				state.kgy$setRequiresTool(v);
			}
		}
	}
}
