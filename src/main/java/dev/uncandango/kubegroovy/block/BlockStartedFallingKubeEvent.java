package dev.uncandango.kubegroovy.block;

import dev.uncandango.kubegroovy.entity.KubeEntityEvent;
import dev.uncandango.kubegroovy.level.LevelBlock;
import dev.uncandango.kubegroovy.typings.Info;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

@Info(value = """
	Invoked when a falling block starts to fall.
	""")
public class BlockStartedFallingKubeEvent implements KubeEntityEvent {
	private final LevelBlock block;
	private final FallingBlockEntity entity;

	public BlockStartedFallingKubeEvent(Level level, BlockPos pos, BlockState state, FallingBlockEntity entity) {
		this.block = level.kgy$getBlock(pos).cache(state);
		this.entity = entity;
	}

	@Override
	public Level getLevel() {
		return block.getLevel();
	}

	@Override
	public Entity getEntity() {
		return entity;
	}

	public LevelBlock getBlock() {
		return block;
	}
}