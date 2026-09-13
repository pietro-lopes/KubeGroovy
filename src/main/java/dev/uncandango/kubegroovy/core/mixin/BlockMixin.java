package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.block.BlockBuilder;
import dev.uncandango.kubegroovy.core.BlockKGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

@Mixin(Block.class)
@RemapPrefixForGY("kgy$")
public abstract class BlockMixin implements BlockKGY {
	@Unique
	private @Nullable String kgy$id;

	@Unique
	private @Nullable BlockBuilder kgy$blockBuilder;

	@Unique
	private @Nullable Map<String, Object> kgy$typeData;

	@Override
	public String kgy$getId() {
		if (kgy$id == null) {
			kgy$id = kgy$getBlock().builtInRegistryHolder().key().identifier().toString();
		}

		return kgy$id;
	}

	@Override
	@Nullable
	public BlockBuilder kgy$getBlockBuilder() {
		return kgy$blockBuilder;
	}

	@Override
	public void kgy$setBlockBuilder(BlockBuilder b) {
		kgy$blockBuilder = b;
	}

	@Override
	public Map<String, Object> kgy$getTypeData() {
		if (kgy$typeData == null) {
			kgy$typeData = new HashMap<>();
		}

		return kgy$typeData;
	}

	@Inject(method = "getName", at = @At("HEAD"), cancellable = true)
	private void kgy$getName(CallbackInfoReturnable<MutableComponent> cir) {
		if (kgy$blockBuilder != null && kgy$blockBuilder.displayName != null && kgy$blockBuilder.formattedDisplayName) {
			cir.setReturnValue(Component.literal("").append(kgy$blockBuilder.displayName));
		}
	}
}
