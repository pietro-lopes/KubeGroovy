package dev.uncandango.kubegroovy.script.bridge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Minimal type-wrapper registry. Real conversions live in GroovyConversions. */
public class TypeWrappers {
	public final Map<Class<?>, Object> wrappers = new ConcurrentHashMap<>();

	public <T> void register(Class<T> target, TypeWrapperValidator validator, TypeWrapperFactory<T> factory) {
		wrappers.put(target, factory);
	}

	public <T> void register(Class<T> target, TypeWrapperFactory<T> factory) {
		wrappers.put(target, factory);
	}

	public <T> void registerDirect(Class<T> target, TypeWrapperValidator validator, DirectTypeWrapperFactory<T> factory) {
		wrappers.put(target, factory);
	}

	public <T> void registerDirect(Class<T> target, DirectTypeWrapperFactory<T> factory) {
		wrappers.put(target, factory);
	}
}
