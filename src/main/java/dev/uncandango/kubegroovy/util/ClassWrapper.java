package dev.uncandango.kubegroovy.util;

import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.NativeJavaClass;
import dev.uncandango.kubegroovy.script.bridge.Scriptable;
import dev.uncandango.kubegroovy.script.bridge.TypeInfo;
import dev.uncandango.kubegroovy.script.annotations.CustomJavaToGyWrapper;

public record ClassWrapper<T>(Class<T> wrappedClass) implements CustomJavaToGyWrapper {
	@Override
	public Scriptable convertJavaToGy(Context cx, Scriptable scope, TypeInfo staticType) {
		return new NativeJavaClass(cx, scope, wrappedClass);
	}

	@Override
	public String toString() {
		return "ClassWrapper[" + wrappedClass.getName() + "]";
	}
}