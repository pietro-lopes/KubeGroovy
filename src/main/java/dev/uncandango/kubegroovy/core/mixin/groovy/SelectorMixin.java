package dev.uncandango.kubegroovy.core.mixin.groovy;

import org.codehaus.groovy.vmplugin.v8.Selector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {"org.codehaus.groovy.vmplugin.v8.Selector.MethodSelector"})
public class SelectorMixin {


	@Inject(method = "buildInvokeHandle", at = @At(value = "INVOKE", target = "Ljava/lang/invoke/MethodHandles;explicitCastArguments(Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"))
	private void applyPostProcessMh(CallbackInfo ci) {
	}
}
