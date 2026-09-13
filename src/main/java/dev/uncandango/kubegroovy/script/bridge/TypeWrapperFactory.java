package dev.uncandango.kubegroovy.script.bridge;

@FunctionalInterface
public interface TypeWrapperFactory<T> {
	T wrap(Context cx, Object from, TypeInfo target);
}
