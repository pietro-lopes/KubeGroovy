package dev.uncandango.kubegroovy.script.bridge;

/** Legacy-compat java class wrapper. */
public class NativeJavaClass implements Scriptable, Wrapper {
	private final Class<?> clazz;

	public NativeJavaClass(Context cx, Scriptable scope, Class<?> clazz) {
		this.clazz = clazz;
	}

	public Class<?> getClassObject() {
		return clazz;
	}

	@Override
	public Object unwrap() {
		return clazz;
	}
}
