package dev.uncandango.kubegroovy.script.bridge;

/** Minimal legacy-compat factory base. Real engine overrides createContext. */
public class ContextFactory {
	private static ContextFactory global = new ContextFactory();

	public static ContextFactory getGlobal() {
		return global;
	}

	protected Context createContext() {
		return new Context();
	}

	public Context enter() {
		var cx = createContext();
		Context.setCurrent(cx);
		return cx;
	}

	public void exit() {
		Context.setCurrent(null);
	}

	public TypeWrappers getTypeWrappers() {
		return new TypeWrappers();
	}
}
