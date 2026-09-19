package dev.uncandango.kubegroovy.script;

import dev.uncandango.kubegroovy.script.bridge.Context;
import groovy.lang.Closure;
import org.jspecify.annotations.Nullable;

public interface WithScriptContext {
	@Nullable
	Context cx();
}
