package dev.uncandango.kubegroovy.core.mixin.groovy;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.ConvertedClosure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(ConvertedClosure.class)
public class ConvertedClosureMixin {
	@Unique
	private Context kgy$context;

	@Inject(method = "<init>(Lgroovy/lang/Closure;Ljava/lang/String;)V", at = @At("RETURN"))
	private void kgy$captureContext(Closure<?> closure, String methodName, CallbackInfo ci) {
		kgy$context = Context.getCurrent();
		if (kgy$context == null) {
			kgy$context = Context.from(closure);
		}
	}

	@WrapOperation(method = "invokeCustom", at = @At(value = "INVOKE", target = "Lgroovy/lang/Closure;call([Ljava/lang/Object;)Ljava/lang/Object;"))
	private Object kgy$withContext(Closure<?> closure, Object[] args, Operation<Object> original) throws Exception {
		var context = kgy$context;
		if (context == null) {
			context = Context.from(closure);
		}
		if (context == null) {
			return original.call(closure, args);
		}
		if (Context.getCurrent() == context) {
			return original.call(closure, args);
		}
		var capturedContext = context;
		return ScopedValue.where(Context.CURRENT, capturedContext).call(() -> original.call(closure, args));
	}

	@WrapOperation(method = "invokeCustom", at = @At(value = "INVOKE", target = "Lorg/codehaus/groovy/runtime/typehandling/DefaultTypeTransformation;castToType(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;"))
	private Object kgy$asType(Object object, Class type, Operation<Object> original, @Local(name = "method", argsOnly = true) Method method){
		try {
			return original.call(object, type);
		} catch (RuntimeException e) {
			var context = Context.from(this);
			if (context == null) throw e;
			return context.gyToJava(object, TypeInfo.of(method.getGenericReturnType()));
		}
	}
}
