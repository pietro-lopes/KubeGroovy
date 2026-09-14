package dev.uncandango.kubegroovy.script.bridge;

import groovy.lang.GroovyObjectSupport;

/** Legacy-compat java object wrapper. */
public class NativeJavaObject extends GroovyObjectSupport implements Scriptable, Wrapper {
	protected final Object object;
	protected final Scriptable scope;
	protected final TypeInfo type;

	public NativeJavaObject(Object object) {
		this.object = object;
		this.scope = null;
		this.type = TypeInfo.NONE;
	}

	public NativeJavaObject(Scriptable scope, Object object, TypeInfo type, Context cx) {
		this.scope = scope;
		this.object = object;
		this.type = type == null ? TypeInfo.NONE : type;
	}

	@Override
	public Object unwrap() {
		return object;
	}

	public Object getObject() {
		return object;
	}
}
