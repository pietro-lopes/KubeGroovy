package dev.uncandango.kubegroovy.script.bridge;

/** Minimal legacy-compat factory base. Real engine overrides createContext. */
public class ContextFactory {
	protected Context createContext() {
		return new Context();
	}

	@Deprecated
	public Context enter() {
		throw new UnsupportedOperationException();
	}

	public TypeWrappers getTypeWrappers() {
		return new TypeWrappers();
	}
}
