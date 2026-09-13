package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.script.KubeGroovyBackgroundThread;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Util.class)
public abstract class UtilMixin {
	@Inject(method = "shutdownExecutors", at = @At("RETURN"))
	private static void shutdownExecutorsKGY(CallbackInfo ci) {
		KubeGroovyBackgroundThread.shutdown();
	}
}
