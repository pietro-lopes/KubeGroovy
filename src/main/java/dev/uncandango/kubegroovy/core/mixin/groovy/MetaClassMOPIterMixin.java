package dev.uncandango.kubegroovy.core.mixin.groovy;

import dev.uncandango.kubegroovy.script.groovy.GuardedMetaMethod;
import dev.uncandango.kubegroovy.script.groovy.TypeWrapperGeneratedMethod;
import groovy.lang.MetaMethod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// Security wrappers must not prevent Groovy from selecting its nonvirtual super bridges.
@Mixin(targets = "groovy.lang.MetaClassImpl$1MOPIter")
public abstract class MetaClassMOPIterMixin {
	@Inject(method = "isDGM", at = @At("HEAD"), cancellable = true)
	private void kgy$extensionIdentity(MetaMethod method, CallbackInfoReturnable<Boolean> ci) {
		if (method instanceof GuardedMetaMethod guarded) ci.setReturnValue(guarded.isGroovyExtension());
		else if (method instanceof TypeWrapperGeneratedMethod wrapped) ci.setReturnValue(wrapped.isGroovyExtension());
	}
}