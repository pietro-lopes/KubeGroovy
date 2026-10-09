package dev.uncandango.kubegroovy.core.mixin.groovy;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.uncandango.kubegroovy.KubeGroovy;
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// for debug
@Mixin(DefaultTypeTransformation.class)
public class DefaultTypeTransformationMixin {
	@Inject(method = "castToType", at = @At(value = "HEAD"))
	private static void kgy$logWhoCallsAsType(Object object, Class type, CallbackInfoReturnable<Object> cir){
		// KubeGroovy.LOGGER.info("AsType called for class {}, and obj {}", type, object);
	}
}
