package dev.uncandango.kubegroovy.core.mixin.groovy;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.uncandango.kubegroovy.script.WithScriptContext;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.ConvertedClosure;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(ConvertedClosure.class)
public class ConvertedClosureMixin implements WithScriptContext {
	@Unique
	private Context kgy$context;

	@Inject(method = "<init>(Lgroovy/lang/Closure;Ljava/lang/String;)V", at = @At("RETURN"))
	private void kgy$captureContext(Closure<?> closure, String methodName, CallbackInfo ci) {
		kgy$context = Context.from(closure);
	}

	@WrapOperation(method = "invokeCustom", at = @At(value = "INVOKE", target = "Lgroovy/lang/Closure;call([Ljava/lang/Object;)Ljava/lang/Object;"))
	private Object kgy$withContext(Closure<?> closure, Object[] args, Operation<Object> original) throws Exception {
		return ScopedValue.where(Context.CURRENT, kgy$context).call(() -> original.call(closure, args));
	}

	@WrapOperation(method = "invokeCustom", at = @At(value = "INVOKE", target = "Lorg/codehaus/groovy/runtime/typehandling/DefaultTypeTransformation;castToType(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;"))
	private Object kgy$asType(Object object, Class type, Operation<Object> original, @Local(name = "method", argsOnly = true) Method method){
		try {
			return ScopedValue.where(Context.CURRENT, kgy$context).call(() -> original.call(object, type));
		} catch (RuntimeException e) {
			return ScopedValue.where(Context.CURRENT, kgy$context).call(() -> kgy$context.gyToJava(object, TypeInfo.of(method.getGenericReturnType())));
		}
	}

	@Override
	public @Nullable Context cx() {
		return kgy$context;
	}
}
