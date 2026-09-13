package dev.uncandango.kubegroovy.script.bridge;

/** Legacy-compat wrapper helper. Groovy port generally passes objects through. */
public interface Wrapper {
	Object unwrap();

	static Object unwrapped(Object o) {
		while (o instanceof Wrapper w) {
			o = w.unwrap();
		}
		// Unwrap Groovy closures holding a single delegate? Keep simple: identity.
		return o;
	}
}
