package dev.uncandango.kubegroovy.script.annotations;

import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.Scriptable;
import dev.uncandango.kubegroovy.script.bridge.TypeInfo;

/** Legacy-compat custom Java-to-script wrapper hook. */
public interface CustomJavaToGyWrapper {
	default Scriptable convertJavaToGy(Context cx, Scriptable scope, TypeInfo target) {
		return scope;
	}
}
