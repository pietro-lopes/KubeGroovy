package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LevelAccessor.class)
public interface LevelAccessorMixin {
	@Shadow
	@RemapForGY("getTime")
	long getGameTime();
}
