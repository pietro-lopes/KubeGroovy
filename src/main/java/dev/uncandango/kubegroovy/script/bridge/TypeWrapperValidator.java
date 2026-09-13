package dev.uncandango.kubegroovy.script.bridge;

@FunctionalInterface
public interface TypeWrapperValidator {
	boolean test(Object from, TypeInfo target);

	default boolean shouldWrap(Class<?> from, Class<?> to) {
		return true;
	}
}
