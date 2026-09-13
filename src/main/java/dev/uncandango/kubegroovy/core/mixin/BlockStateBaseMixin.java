package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.BlockStateKGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBehaviour.BlockStateBase.class)
@RemapPrefixForGY("kgy$")
public abstract class BlockStateBaseMixin implements BlockStateKGY {
	@Shadow
	protected abstract BlockState asState();

	@Override
	@Accessor("destroySpeed")
	@Mutable
	public abstract void kgy$setDestroySpeed(float v);

	@Override
	@Accessor("requiresCorrectToolForDrops")
	@Mutable
	public abstract void kgy$setRequiresTool(boolean v);

	@Override
	@Accessor("lightEmission")
	@Mutable
	public abstract void kgy$setLightEmission(int v);

	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void kgy$onRandomTick(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (kgy$randomTickOverride(asState(), level, pos, random)) {
			ci.cancel();
		}
	}
}
