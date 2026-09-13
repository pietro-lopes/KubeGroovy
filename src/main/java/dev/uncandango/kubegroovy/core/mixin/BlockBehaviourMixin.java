package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.block.callback.RandomTickCallback;
import dev.uncandango.kubegroovy.core.BlockBehaviourKGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(BlockBehaviour.class)
@RemapPrefixForGY("kgy$")
public abstract class BlockBehaviourMixin implements BlockBehaviourKGY {
	@Unique
	@Nullable
	private Consumer<RandomTickCallback> kgy$randomTickCallback;

	@Override
	public void kgy$setRandomTickCallback(Consumer<RandomTickCallback> callback) {
		kgy$setIsRandomlyTicking(true);
		this.kgy$randomTickCallback = callback;
	}

	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void onRandomTick(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, RandomSource randomSource, CallbackInfo ci) {
		if (kgy$randomTickCallback != null) {
			kgy$randomTickCallback.accept(new RandomTickCallback(serverLevel.kgy$getBlock(blockPos).cache(blockState), randomSource));
			ci.cancel();
		}
	}

	@Override
	@Accessor("hasCollision")
	@Mutable
	public abstract void kgy$setHasCollision(boolean v);

	@Override
	@Accessor("explosionResistance")
	@Mutable
	public abstract void kgy$setExplosionResistance(float v);

	@Override
	@Accessor("isRandomlyTicking")
	@Mutable
	public abstract void kgy$setIsRandomlyTicking(boolean v);

	@Override
	@Accessor("soundType")
	@Mutable
	public abstract void kgy$setSoundType(SoundType v);

	@Override
	@Accessor("friction")
	@Mutable
	public abstract void kgy$setFriction(float v);

	@Override
	@Accessor("speedFactor")
	@Mutable
	public abstract void kgy$setSpeedFactor(float v);

	@Override
	@Accessor("jumpFactor")
	@Mutable
	public abstract void kgy$setJumpFactor(float v);

	@Override
	@Accessor("descriptionId")
	@Mutable
	public abstract void kgy$setNameKey(String key);
}
