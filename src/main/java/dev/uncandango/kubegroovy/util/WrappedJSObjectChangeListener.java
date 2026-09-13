package dev.uncandango.kubegroovy.util;

@FunctionalInterface
public interface WrappedJSObjectChangeListener<T> {
	void onChanged(T o);
}