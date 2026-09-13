package dev.uncandango.kubegroovy.block.custom;

import dev.uncandango.kubegroovy.block.BlockBuilder;
import dev.uncandango.kubegroovy.util.TickDuration;
import dev.uncandango.kubegroovy.script.annotations.ReturnsSelf;
import net.minecraft.world.level.block.state.properties.BlockSetType;

public interface ButtonOrPressurePlateBuilder {
	@ReturnsSelf
	BlockBuilder behaviour(BlockSetType behaviour);

	@ReturnsSelf
	BlockBuilder ticksToStayPressed(TickDuration ticks);
}
