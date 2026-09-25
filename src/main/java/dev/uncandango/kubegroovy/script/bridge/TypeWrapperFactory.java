package dev.uncandango.kubegroovy.script.bridge;

import dev.uncandango.kubegroovy.script.type.TypeInfo;

@FunctionalInterface
public interface TypeWrapperFactory<T> {
	T wrap(Object from, TypeInfo target);
}
