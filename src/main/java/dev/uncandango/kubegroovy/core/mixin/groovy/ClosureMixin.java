package dev.uncandango.kubegroovy.core.mixin.groovy;

import dev.uncandango.kubegroovy.script.bridge.Context;
import groovy.lang.Closure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Closure.class)
public abstract class ClosureMixin {
	@Unique
	private Context kgy$context;

	@Inject(method = "<init>(Ljava/lang/Object;Ljava/lang/Object;)V", at = @At("RETURN"))
	private void kgy$captureContext(Object owner, Object thisObject, CallbackInfo ci) {
		kgy$context = Context.getCurrent();
		if (kgy$context == null) {
			kgy$context = Context.from(owner);
		}
	}

	@Inject(method = "run", at = @At("HEAD"), cancellable = true)
	private void kgy$runWithContext(CallbackInfo ci) {
		if (kgy$context == null || Context.getCurrent() == kgy$context) {
			return;
		}

		var closure = (Closure<?>) (Object) this;
		ScopedValue.where(Context.CURRENT, kgy$context).run(closure::call);
		ci.cancel();
	}
}
