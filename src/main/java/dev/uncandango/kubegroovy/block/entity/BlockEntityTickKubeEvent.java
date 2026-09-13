package dev.uncandango.kubegroovy.block.entity;

import dev.uncandango.kubegroovy.level.KubeLevelEvent;
import dev.uncandango.kubegroovy.level.LevelBlock;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullUnmarked;

public class BlockEntityTickKubeEvent implements KubeLevelEvent {
	private final KubeBlockEntity entity;

	public BlockEntityTickKubeEvent(KubeBlockEntity entity) {
		this.entity = entity;
	}

	@Override
	@NullUnmarked
	public Level getLevel() {
		return entity.getLevel();
	}

	public LevelBlock getBlock() {
		return entity.getBlock();
	}

	public int getTick() {
		return entity.tick;
	}

	public int getCycle() {
		return entity.cycle;
	}
}
