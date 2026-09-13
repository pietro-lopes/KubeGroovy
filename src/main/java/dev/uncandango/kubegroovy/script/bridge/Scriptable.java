package dev.uncandango.kubegroovy.script.bridge;

/** Minimal legacy-compat marker for a script scope object. In Groovy port this is usually a Binding/Map. */
public interface Scriptable {
	Object NOT_FOUND = new Object();

	default String getClassName() {
		return getClass().getSimpleName();
	}

	default Object get(Context cx, String name, Scriptable start) {
		return NOT_FOUND;
	}

	default Object get(Context cx, int index, Scriptable start) {
		return NOT_FOUND;
	}

	default Object get(Context cx, Symbol key, Scriptable start) {
		return NOT_FOUND;
	}

	default void put(Context cx, String name, Scriptable start, Object value) {
	}

	default void put(Context cx, int index, Scriptable start, Object value) {
	}

	default void put(Context cx, Symbol key, Scriptable start, Object value) {
	}

	default void delete(Context cx, String name) {
	}

	default void delete(Context cx, int index) {
	}

	default Scriptable construct(Context cx, Scriptable scope, Object[] args) {
		return null;
	}
}
