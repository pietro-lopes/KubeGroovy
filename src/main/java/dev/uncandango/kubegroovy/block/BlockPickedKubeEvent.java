package dev.uncandango.kubegroovy.block;

import dev.uncandango.kubegroovy.entity.KubeRayTraceResult;
import dev.uncandango.kubegroovy.level.LevelBlock;
import dev.uncandango.kubegroovy.player.KubePlayerEvent;
import dev.uncandango.kubegroovy.plugin.builtin.event.BlockEvents;
import dev.uncandango.kubegroovy.typings.Info;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

@Info(value = """
	Invoked when player middle-clicks on a block.
	""")
public class BlockPickedKubeEvent implements KubePlayerEvent {
	public final Level level;
	public final LevelBlock block;
	public final Player player;
	private final HitResult hitResult;
	private @Nullable KubeRayTraceResult target;

	public BlockPickedKubeEvent(Level level, BlockPos pos, BlockState state, Player player, HitResult hitResult) {
		this.level = level;
		this.block = level.kgy$getBlock(pos).cache(state);
		this.player = player;
		this.hitResult = hitResult;
	}

	@Override
	public Level getLevel() {
		return block.getLevel();
	}

	@Override
	public Player getEntity() {
		return player;
	}

	public KubeRayTraceResult getTarget() {
		if (target == null) {
			target = new KubeRayTraceResult(player, hitResult);
		}

		return target;
	}

	@Nullable
	@HideFromGY
	public static ItemStack handle(BlockState state, HitResult target, LevelReader levelReader, BlockPos pos, Player player) {
		if (levelReader instanceof Level level) {
			var key = state.kgy$getKey();

			if (BlockEvents.PICKED.hasListeners(key) && BlockEvents.PICKED.post(level, key, new BlockPickedKubeEvent(level, pos, state, player, target)).value() instanceof ItemStack stack) {
				return stack;
			}
		}

		return null;
	}
}