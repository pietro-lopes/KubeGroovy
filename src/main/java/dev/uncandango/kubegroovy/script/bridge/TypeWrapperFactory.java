package dev.uncandango.kubegroovy.script.bridge;

import dev.uncandango.kubegroovy.script.type.TypeInfo;

@FunctionalInterface
public interface TypeWrapperFactory<T> {
	T wrap(Context cx, Object from, TypeInfo target);
}
