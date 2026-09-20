package dev.uncandango.kubegroovy.script.bridge;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Minimal legacy-compat factory base. Real engine overrides createContext. */
public class ContextFactory {
	private final Map<Class<?>, Object[]> defaultRecordProperties = new IdentityHashMap<>();

	protected Context createContext() {
		return new Context(this);
	}

	@Deprecated
	public Context enter() {
		throw new UnsupportedOperationException();
	}

	public TypeWrappers getTypeWrappers() {
		return new TypeWrappers();
	}

	@Nullable
	public synchronized Object[] getDefaultRecordProperties(Class<?> type) {
		return defaultRecordProperties.get(type);
	}

	public synchronized void registerDefaultRecordProperties(Record record) {
		try {
			var components = record.getClass().getRecordComponents();
			var properties = new Object[components.length];

			for (int i = 0; i < components.length; i++) {
				properties[i] = components[i].getAccessor().invoke(record);
			}

			defaultRecordProperties.put(record.getClass(), properties);
		} catch (IllegalAccessException | InvocationTargetException ex) {
			throw new RuntimeException(ex);
		}
	}
}
