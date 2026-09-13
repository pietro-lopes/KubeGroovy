package dev.uncandango.kubegroovy.block;

import dev.uncandango.kubegroovy.level.LevelBlock;
import dev.uncandango.kubegroovy.player.KubePlayerEvent;
import dev.uncandango.kubegroovy.typings.Info;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

@Info(value = """
	Invoked when a block is destroyed by a player.
	""")
public class BlockBrokenKubeEvent implements KubePlayerEvent {
	private final BreakBlockEvent event;

	public BlockBrokenKubeEvent(BreakBlockEvent event) {
		this.event = event;
	}

	@Override
	@Info("The player that broke the block.")
	public Player getEntity() {
		return event.getPlayer();
	}

	@Info("The block that was broken.")
	public LevelBlock getBlock() {
		return ((Level) event.getLevel()).kgy$getBlock(event.getPos()).cache(event.getState());
	}
}