package dev.uncandango.kubegroovy.core.mixin.groovy;

import dev.uncandango.kubegroovy.script.ScriptManager;
import dev.uncandango.kubegroovy.script.groovy.SandboxRuntime;
import org.codehaus.groovy.reflection.CachedField;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Mixin(CachedField.class)
public abstract class CachedFieldMixin {
	@Shadow @Final public Field field;

	@Inject(method = "getProperty", at = @At("HEAD"))
	private void kgy$checkRead(Object object, CallbackInfoReturnable<Object> ci) {
		if (SandboxRuntime.inScript()) kgy$check(object);
	}
	@Inject(method = "setProperty", at = @At("HEAD"))
	private void kgy$checkWrite(Object object, Object value, CallbackInfo ci) {
		if (SandboxRuntime.inScript()) {
			kgy$check(object);
			SandboxRuntime.checkValue(value);
		}
	}
	@ModifyReturnValue(method = "getProperty", at = @At("RETURN"))
	private Object kgy$checkResult(Object value) {
		return SandboxRuntime.inScript() ? SandboxRuntime.checkValue(value) : value;
	}
	@Unique
	private void kgy$check(Object object) {
		var type = object instanceof Class<?> clazz ? clazz : object == null ? field.getDeclaringClass() : object.getClass();
		if (!ScriptManager.getClassFilter().isFieldAllowed(type, field)) throw SandboxRuntime.blocked("field " + field);
	}
}
