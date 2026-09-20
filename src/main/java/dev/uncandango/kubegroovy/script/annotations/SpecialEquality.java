package dev.uncandango.kubegroovy.script.annotations;

import dev.uncandango.kubegroovy.script.bridge.Context;
import org.jspecify.annotations.Nullable;

/** Legacy-compat equality hook for scripts. */
// TODO: implement this on sandbox transformations
public interface SpecialEquality {
	default boolean specialEquals(Context cx, @Nullable Object o, boolean shallow) {
		return equals(o);
	}
}
