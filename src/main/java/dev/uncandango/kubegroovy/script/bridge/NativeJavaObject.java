package dev.uncandango.kubegroovy.script.bridge;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import groovy.lang.GroovyObjectSupport;

/** Legacy-compat java object wrapper. */
public class NativeJavaObject extends GroovyObjectSupport implements Wrapper { //  implements Scriptable,
	protected final Object object;
	protected final TypeInfo type;

	public NativeJavaObject(Object object) {
		this.object = object;
		this.type = TypeInfo.NONE;
	}

	public NativeJavaObject(Object object, TypeInfo type, Context cx) {
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
