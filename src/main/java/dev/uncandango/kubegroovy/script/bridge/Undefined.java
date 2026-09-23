package dev.uncandango.kubegroovy.script.bridge;

/** Legacy-compat undefined marker. Groovy port uses null. */
public final class Undefined {
	public static final Object INSTANCE = null;
	//public static final Scriptable SCRIPTABLE_INSTANCE = new ScriptableObject();

	private Undefined() {
	}

	public static boolean isUndefined(Object o) {
		return o == null || o == INSTANCE;
	}
}
