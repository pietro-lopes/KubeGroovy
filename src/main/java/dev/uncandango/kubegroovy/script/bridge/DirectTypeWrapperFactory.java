package dev.uncandango.kubegroovy.script.bridge;

import dev.uncandango.kubegroovy.script.type.TypeInfo;

@FunctionalInterface
public interface DirectTypeWrapperFactory<T> extends TypeWrapperFactory<T> {
	T wrap(Object from);

	@Override
	default T wrap(Context cx, Object from, TypeInfo target) {
		return wrap(from);
	}
}
