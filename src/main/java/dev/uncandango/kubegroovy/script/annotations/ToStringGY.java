package dev.uncandango.kubegroovy.script.annotations;

import dev.uncandango.kubegroovy.script.bridge.Context;

/** Legacy-compat toString hook for scripts. */
public interface ToStringGY {
	default String toStringGY(Context cx) {
		return toString();
	}
}
