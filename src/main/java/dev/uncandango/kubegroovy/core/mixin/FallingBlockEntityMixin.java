package dev.uncandango.kubegroovy.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.uncandango.kubegroovy.block.BlockStartedFallingKubeEvent;
import dev.uncandango.kubegroovy.block.BlockStoppedFallingKubeEvent;
import dev.uncandango.kubegroovy.plugin.builtin.event.BlockEvents;
import dev.uncandango.kubegroovy.script.ScriptType;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FallingBlockEntity.class)
public class FallingBlockEntityMixin {
	@Shadow
	private BlockState blockState;

	@Inject(method = "fall", cancellable = true, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z", shift = At.Shift.BEFORE))
	private static void kgy$fallStart(Level level, BlockPos pos, BlockState state, CallbackInfoReturnable<FallingBlockEntity> cir, @Local(name = "entity") FallingBlockEntity entity) {
		if (!level.isClientSide() && BlockEvents.STARTED_FALLING.hasListeners(state.kgy$getKey())) {
			if (BlockEvents.STARTED_FALLING.post(ScriptType.SERVER, state.kgy$getKey(), new BlockStartedFallingKubeEvent(level, pos, state, entity)).interruptFalse()) {
				cir.setReturnValue(entity);
			}
		}
	}

	@Unique
	private double kgy$lastFallSpeed;

	@Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/FallingBlockEntity;applyGravity()V", shift = At.Shift.BEFORE))
	private void kgy$captureFallSpeed(CallbackInfo ci) {
		kgy$lastFallSpeed = Math.abs(((FallingBlockEntity) (Object) this).getDeltaMovement().y());
	}

	@Inject(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ChunkMap;sendToTrackingPlayers(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/protocol/Packet;)V",
			shift = At.Shift.BEFORE
		)
	)
	private void kgy$fallEnd(CallbackInfo ci, @Local(name = "pos") BlockPos pos, @Local(name = "currentState") BlockState replacedState) {
		var entity = (FallingBlockEntity) (Object) this;
		if (!entity.level().isClientSide() && BlockEvents.STOPPED_FALLING.hasListeners(blockState.kgy$getKey())) {
			BlockEvents.STOPPED_FALLING.post(ScriptType.SERVER, blockState.kgy$getKey(), new BlockStoppedFallingKubeEvent(entity.level(), pos, blockState, entity, kgy$lastFallSpeed, replacedState));
		}
	}

	@Unique
	@RemapForGY("setBlockState")
	public void kgy$setBlockState(BlockState state) {
		this.blockState = state;
	}
}
