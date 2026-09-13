package dev.uncandango.kubegroovy.plugin.builtin.event;

import dev.uncandango.kubegroovy.block.BlockBrokenKubeEvent;
import dev.uncandango.kubegroovy.block.BlockDropsKubeEvent;
import dev.uncandango.kubegroovy.block.BlockLeftClickedKubeEvent;
import dev.uncandango.kubegroovy.block.BlockModificationKubeEvent;
import dev.uncandango.kubegroovy.block.BlockPickedKubeEvent;
import dev.uncandango.kubegroovy.block.BlockPlacedKubeEvent;
import dev.uncandango.kubegroovy.block.BlockRightClickedKubeEvent;
import dev.uncandango.kubegroovy.block.BlockStartedFallingKubeEvent;
import dev.uncandango.kubegroovy.block.BlockStoppedFallingKubeEvent;
import dev.uncandango.kubegroovy.block.DetectorBlockKubeEvent;
import dev.uncandango.kubegroovy.block.FarmlandTrampledKubeEvent;
import dev.uncandango.kubegroovy.block.RandomTickKubeEvent;
import dev.uncandango.kubegroovy.block.entity.BlockEntityTickKubeEvent;
import dev.uncandango.kubegroovy.event.EventGroup;
import dev.uncandango.kubegroovy.event.EventHandler;
import dev.uncandango.kubegroovy.event.EventTargetType;
import dev.uncandango.kubegroovy.event.TargetedEventHandler;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.ItemWrapper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;

public interface BlockEvents {
	EventGroup GROUP = EventGroup.of("BlockEvents");
	EventTargetType<ResourceKey<Block>> TARGET = EventTargetType.registryKey(Registries.BLOCK, Block.class);

	EventHandler MODIFICATION = GROUP.startup("modification", () -> BlockModificationKubeEvent.class);
	TargetedEventHandler<ResourceKey<Block>> RIGHT_CLICKED = GROUP.common("rightClicked", () -> BlockRightClickedKubeEvent.class).hasResult().supportsTarget(TARGET);
	TargetedEventHandler<ResourceKey<Block>> LEFT_CLICKED = GROUP.common("leftClicked", () -> BlockLeftClickedKubeEvent.class).hasResult().supportsTarget(TARGET);
	TargetedEventHandler<ResourceKey<Block>> PLACED = GROUP.common("placed", () -> BlockPlacedKubeEvent.class).hasResult().supportsTarget(TARGET);
	TargetedEventHandler<ResourceKey<Block>> BROKEN = GROUP.common("broken", () -> BlockBrokenKubeEvent.class).hasResult().supportsTarget(TARGET);
	TargetedEventHandler<ResourceKey<Block>> DROPS = GROUP.server("drops", () -> BlockDropsKubeEvent.class).hasResult().supportsTarget(TARGET);
	TargetedEventHandler<String> DETECTOR_CHANGED = GROUP.common("detectorChanged", () -> DetectorBlockKubeEvent.class).supportsTarget(EventTargetType.STRING);
	TargetedEventHandler<String> DETECTOR_POWERED = GROUP.common("detectorPowered", () -> DetectorBlockKubeEvent.class).supportsTarget(EventTargetType.STRING);
	TargetedEventHandler<String> DETECTOR_UNPOWERED = GROUP.common("detectorUnpowered", () -> DetectorBlockKubeEvent.class).supportsTarget(EventTargetType.STRING);
	TargetedEventHandler<ResourceKey<Block>> FARMLAND_TRAMPLED = GROUP.common("farmlandTrampled", () -> FarmlandTrampledKubeEvent.class).hasResult().supportsTarget(TARGET);
	TargetedEventHandler<ResourceKey<Block>> RANDOM_TICK = GROUP.server("randomTick", () -> RandomTickKubeEvent.class).hasResult().requiredTarget(TARGET);
	TargetedEventHandler<ResourceKey<Block>> BLOCK_ENTITY_TICK = GROUP.common("blockEntityTick", () -> BlockEntityTickKubeEvent.class).requiredTarget(TARGET);
	TargetedEventHandler<ResourceKey<Block>> STARTED_FALLING = GROUP.common("startedFalling", () -> BlockStartedFallingKubeEvent.class).hasResult().supportsTarget(TARGET);
	TargetedEventHandler<ResourceKey<Block>> STOPPED_FALLING = GROUP.common("stoppedFalling", () -> BlockStoppedFallingKubeEvent.class).supportsTarget(TARGET);
	TargetedEventHandler<ResourceKey<Block>> PICKED = GROUP.common("picked", () -> BlockPickedKubeEvent.class).hasResult(ItemWrapper.TYPE_INFO).supportsTarget(TARGET);
}
