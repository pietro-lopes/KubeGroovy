package dev.uncandango.kubegroovy.script.annotations;

/** Legacy-compat remapped enum constant hook. */
public interface RemappedEnumConstant {
	default String getRemappedEnumConstantName() {
		return toString();
	}
}
