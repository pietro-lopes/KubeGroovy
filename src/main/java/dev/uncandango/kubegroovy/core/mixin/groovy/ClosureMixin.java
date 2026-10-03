package dev.uncandango.kubegroovy.core.mixin.groovy;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.uncandango.kubegroovy.script.WithScriptContext;
import dev.uncandango.kubegroovy.script.bridge.Context;
import groovy.lang.Closure;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Closure.class)
public abstract class ClosureMixin implements WithScriptContext {
	@Unique
	private Context kgy$context;

	@Inject(method = "<init>(Ljava/lang/Object;Ljava/lang/Object;)V", at = @At("RETURN"))
	private void kgy$captureContext(Object owner, Object thisObject, CallbackInfo ci) {
		kgy$context = Context.from(owner);
	}

	@WrapMethod(method = "run")
	private void kgy$runWithContext(Operation<Void> original) {
		ScopedValue.where(Context.CURRENT, kgy$context).run(original::call);
	}

	@Override
	public @Nullable Context cx() {
		return kgy$context;
	}
}
