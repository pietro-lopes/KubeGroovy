package dev.uncandango.kubegroovy.script.bridge;

import dev.uncandango.kubegroovy.script.type.TypeInfo;

@FunctionalInterface
public interface TypeWrapperValidator {
	TypeWrapperValidator ALWAYS_VALID = (from, target) -> true;
	boolean test(Object from, TypeInfo target);

	default boolean shouldWrap(Class<?> from, Class<?> to) {
		return true;
	}
}
