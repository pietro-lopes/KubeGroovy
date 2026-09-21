package dev.uncandango.kubegroovy.core.mixin.groovy;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import groovy.lang.GroovyRuntimeException;
import org.codehaus.groovy.runtime.ConvertedClosure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.lang.reflect.Method;

@Mixin(ConvertedClosure.class)
public class ConvertedClosureMixin {
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
