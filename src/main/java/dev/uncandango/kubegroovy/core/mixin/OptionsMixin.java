package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.client.KubeGroovyClient;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

@Mixin(Options.class)
public class OptionsMixin {
	@Shadow
	@Final
	private File optionsFile;

	@Inject(method = "load", at = @At("HEAD"))
	private void loadKGY(CallbackInfo ci) {
		KubeGroovyClient.copyDefaultOptionsFile(optionsFile);
	}
}
