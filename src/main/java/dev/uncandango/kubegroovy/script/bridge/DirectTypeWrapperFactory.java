package dev.uncandango.kubegroovy.script.bridge;

@FunctionalInterface
public interface DirectTypeWrapperFactory<T> {
	T wrap(Object from);
}
