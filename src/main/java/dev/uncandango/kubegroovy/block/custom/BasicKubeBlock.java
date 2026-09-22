package dev.uncandango.kubegroovy.block.custom;

import dev.uncandango.kubegroovy.block.BlockBuilder;
import dev.uncandango.kubegroovy.block.BlockRightClickedKubeEvent;
import dev.uncandango.kubegroovy.block.KubeGroovyBlockProperties;
import dev.uncandango.kubegroovy.block.callback.AfterEntityFallenOnBlockCallback;
import dev.uncandango.kubegroovy.block.callback.BlockExplodedCallback;
import dev.uncandango.kubegroovy.block.callback.BlockStateMirrorCallback;
import dev.uncandango.kubegroovy.block.callback.BlockStateModifyCallback;
import dev.uncandango.kubegroovy.block.callback.BlockStateModifyPlacementCallback;
import dev.uncandango.kubegroovy.block.callback.BlockStateRotateCallback;
import dev.uncandango.kubegroovy.block.callback.CanBeReplacedCallback;
import dev.uncandango.kubegroovy.block.callback.EntityBlockCallback;
import dev.uncandango.kubegroovy.block.callback.EntityFallenOnBlockCallback;
import dev.uncandango.kubegroovy.block.callback.RandomTickCallback;
import dev.uncandango.kubegroovy.block.entity.InventoryAttachment;
import dev.uncandango.kubegroovy.block.entity.KubeBlockEntity;
import dev.uncandango.kubegroovy.script.ScriptType;
import dev.uncandango.kubegroovy.script.ScriptTypeHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

public class BasicKubeBlock extends Block implements SimpleWaterloggedBlock {
	public static class Builder extends BlockBuilder {
		public Builder(Identifier i) {
			super(i);
		}

		@Override
		public Block createObject() {
			return blockEntityInfo != null ? new WithEntity(this) : new BasicKubeBlock(this);
		}
	}

	@SuppressWarnings("DataFlowIssue") // safe
	public static class WithEntity extends BasicKubeBlock implements EntityBlock {
		public WithEntity(BlockBuilder p) {
			super(p);
		}

		@Nullable
		@Override
		public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
			return blockBuilder.kgy$getBlockEntityInfo().createBlockEntity(pos, state);
		}

		@Nullable
		@Override
		public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
			return blockBuilder.kgy$getBlockEntityInfo().getTicker(level);
		}
	}

	public final BlockBuilder blockBuilder;
	public final VoxelShape shape;

	public BasicKubeBlock(BlockBuilder p) {
		super(p.createProperties());
		blockBuilder = p;
		shape = BlockBuilder.createShape(p.kgy$getCustomShape());

		var blockState = stateDefinition.any();
		if (blockBuilder.kgy$getDefaultStateModification() != null) {
			var callbackGY = new BlockStateModifyCallback(blockState);

			if (safeCallback(ScriptType.STARTUP, blockBuilder.kgy$getDefaultStateModification(), callbackGY, "Error while creating default blockState for block " + p.getId())) {
				registerDefaultState(callbackGY.getState());
			}
		} else if (blockBuilder.canBeWaterlogged()) {
			registerDefaultState(blockState.setValue(BlockStateProperties.WATERLOGGED, false));
		}
	}

	@Override
	public BlockBuilder kgy$getBlockBuilder() {
		return blockBuilder;
	}

	@Override
	@Deprecated
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shape;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		if (properties instanceof KubeGroovyBlockProperties kp) {
			for (var property : kp.blockBuilder.kgy$getBlockStateProperties()) {
				builder.add(property);
			}
			kp.blockBuilder.kgy$setBlockStateProperties(Collections.unmodifiableSet(kp.blockBuilder.kgy$getBlockStateProperties()));
		}
	}

	@Override
	@Deprecated
	public FluidState getFluidState(BlockState state) {
		return state.getOptionalValue(BlockStateProperties.WATERLOGGED).orElse(false) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		if (blockBuilder.kgy$getPlacementStateModification() != null) {
			var callbackGY = new BlockStateModifyPlacementCallback(context, this);
			if (safeCallback(context.getLevel(), blockBuilder.kgy$getPlacementStateModification(), callbackGY, "Error while modifying BlockState placement of " + blockBuilder.getId())) {
				return callbackGY.getState();
			}
		}

		if (!blockBuilder.canBeWaterlogged()) {
			return defaultBlockState();
		}

		return defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
	}

	@Override
	public boolean canBeReplaced(BlockState blockState, BlockPlaceContext context) {
		if (blockBuilder.kgy$getCanBeReplacedFunction() != null) {
			var callbackGY = new CanBeReplacedCallback(context, blockState);
			return blockBuilder.kgy$getCanBeReplacedFunction().test(callbackGY);
		}
		return super.canBeReplaced(blockState, context);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getOptionalValue(BlockStateProperties.WATERLOGGED).orElse(false)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}

		return state;
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return blockBuilder.kgy$isTransparent() || !(state.getOptionalValue(BlockStateProperties.WATERLOGGED).orElse(false));
	}

	@Override
	@Deprecated
	public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (blockBuilder.kgy$getRandomTickCallback() != null) {
			var callback = new RandomTickCallback(level.kgy$getBlock(pos), random);
			safeCallback(level, blockBuilder.kgy$getRandomTickCallback(), callback, "Error while random ticking custom block ");
		}
	}

	@Override
	public boolean isRandomlyTicking(BlockState state) {
		return blockBuilder.kgy$getRandomTickCallback() != null;
	}

	@Override
	@Deprecated
	public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return blockBuilder.kgy$isTransparent() ? Shapes.empty() : super.getVisualShape(state, level, pos, ctx);
	}

	@Override
	@Deprecated
	public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
		return blockBuilder.kgy$isTransparent() ? 1F : super.getShadeBrightness(state, level, pos);
	}

	@Override
	@Deprecated
	public boolean skipRendering(BlockState state, BlockState state2, Direction direction) {
		return blockBuilder.kgy$isTransparent() ? (state2.is(this) || super.skipRendering(state, state2, direction)) : super.skipRendering(state, state2, direction);
	}

	private static <T> boolean safeCallback(ScriptTypeHolder holder, Consumer<T> consumer, T value, String errorMessage) {
		try {
			consumer.accept(value);
		} catch (Throwable e) {
			holder.kgy$getScriptType().console.error(errorMessage, e);
			return false;
		}

		return true;
	}

	@Override
	public boolean canPlaceLiquid(@Nullable LivingEntity user, BlockGetter level, BlockPos pos, BlockState state, Fluid type) {
		if (blockBuilder.canBeWaterlogged()) {
			return SimpleWaterloggedBlock.super.canPlaceLiquid(user, level, pos, state, type);
		}

		return false;
	}

	@Override
	public boolean placeLiquid(LevelAccessor levelAccessor, BlockPos blockPos, BlockState blockState, FluidState fluidState) {
		if (blockBuilder.canBeWaterlogged()) {
			return SimpleWaterloggedBlock.super.placeLiquid(levelAccessor, blockPos, blockState, fluidState);
		}

		return false;
	}

	@Override
	public ItemStack pickupBlock(@Nullable LivingEntity user, LevelAccessor level, BlockPos pos, BlockState state) {
		if (blockBuilder.canBeWaterlogged()) {
			return SimpleWaterloggedBlock.super.pickupBlock(user, level, pos, state);
		}

		return ItemStack.EMPTY;
	}

	@Override
	public Optional<SoundEvent> getPickupSound() {
		if (blockBuilder.canBeWaterlogged()) {
			return SimpleWaterloggedBlock.super.getPickupSound();
		}

		return Optional.empty();
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		if (blockBuilder.kgy$getInsideCallback() != null) {
			var callbackGY = new EntityBlockCallback(level, entity, pos, state);
			safeCallback(level, blockBuilder.kgy$getInsideCallback(), callbackGY, "Error while an entity was inside a custom block ");
		} else {
			super.entityInside(state, level, pos, entity, effectApplier, isPrecise);
		}
	}


	@Override
	public void stepOn(Level level, BlockPos blockPos, BlockState blockState, Entity entity) {
		if (blockBuilder.kgy$getStepOnCallback() != null) {
			var callbackGY = new EntityBlockCallback(level, entity, blockPos, blockState);
			safeCallback(level, blockBuilder.kgy$getStepOnCallback(), callbackGY, "Error while an entity stepped on custom block ");
		} else {
			super.stepOn(level, blockPos, blockState, entity);
		}
	}

	@Override
	public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
		if (blockBuilder.kgy$getFallOnCallback() != null) {
			var callbackGY = new EntityFallenOnBlockCallback(level, entity, pos, state, fallDistance);
			safeCallback(level, blockBuilder.kgy$getFallOnCallback(), callbackGY, "Error while an entity fell on custom block ");
		} else {
			super.fallOn(level, state, pos, entity, fallDistance);
		}
	}

	@Override
	public void updateEntityMovementAfterFallOn(BlockGetter level, Entity entity) {
		if (blockBuilder.kgy$getAfterFallenOnCallback() != null) {
			var callbackGY = new AfterEntityFallenOnBlockCallback(level, entity);
			safeCallback(entity, blockBuilder.kgy$getAfterFallenOnCallback(), callbackGY, "Error while bouncing entity from custom block ");
			// if they did not change the entity's velocity, then use the default method to reset the velocity.
			if (!callbackGY.hasChangedVelocity()) {
				super.updateEntityMovementAfterFallOn(level, entity);
			}
		} else {
			super.updateEntityMovementAfterFallOn(level, entity);
		}
	}

	@Override
	public void wasExploded(ServerLevel level, BlockPos pos, Explosion explosion) {
		if (blockBuilder.kgy$getExplodedCallback() != null) {
			var callbackGY = new BlockExplodedCallback(level, pos, explosion);
			safeCallback(level, blockBuilder.kgy$getExplodedCallback(), callbackGY, "Error while exploding custom block ");
		} else {
			super.wasExploded(level, pos, explosion);
		}
	}

	@Override
	public BlockState rotate(BlockState blockState, Rotation rotation) {
		if (blockBuilder.kgy$getRotateStateModification() != null) {
			var callbackGY = new BlockStateRotateCallback(blockState, rotation);
			if (safeCallback(ScriptType.STARTUP, blockBuilder.kgy$getRotateStateModification(), callbackGY, "Error while rotating BlockState of ")) {
				return callbackGY.getState();
			}
		}

		return super.rotate(blockState, rotation);
	}

	@Override
	public BlockState mirror(BlockState blockState, Mirror mirror) {
		if (blockBuilder.kgy$getMirrorStateModification() != null) {
			var callbackGY = new BlockStateMirrorCallback(blockState, mirror);
			if (safeCallback(ScriptType.STARTUP, blockBuilder.kgy$getMirrorStateModification(), callbackGY, "Error while mirroring BlockState of ")) {
				return callbackGY.getState();
			}
		}

		return super.mirror(blockState, mirror);
	}

	@Override
	public InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (blockBuilder.kgy$getRightClick() != null) {
			if (!level.isClientSide()) {
				blockBuilder.kgy$getRightClick().accept(new BlockRightClickedKubeEvent(stack, player, hand, pos, hit.getDirection(), hit));
			}

			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		if (level.getBlockEntity(pos) instanceof KubeBlockEntity entity) {
			InventoryAttachment.onRemove(level, entity);
			level.updateNeighbourForOutputSignal(pos, this.asBlock());
		}
	}


	@Override
	public void setPlacedBy(Level level, BlockPos blockPos, BlockState blockState, @Nullable LivingEntity livingEntity, ItemStack itemStack) {
		if (livingEntity != null && !level.isClientSide() && level.getBlockEntity(blockPos) instanceof KubeBlockEntity e) {
			e.placerId = livingEntity.getUUID();
		}
	}
}
