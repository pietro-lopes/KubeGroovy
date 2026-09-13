package dev.uncandango.kubegroovy.script.bridge;

import java.util.List;

/** Legacy-compat list wrapper. Backed by a plain List for Groovy port. */
public class NativeJavaList extends java.util.AbstractList<Object> implements Scriptable, Wrapper {
	private final List<?> list;

	public NativeJavaList(Context cx, Scriptable scope, Object javaObject, List<?> list, TypeInfo type) {
		this.list = list == null ? List.of() : list;
	}

	@Override
	public Object get(int index) {
		return list.get(index);
	}

	@Override
	public int size() {
		return list.size();
	}

	@Override
	public Object unwrap() {
		return list;
	}
}
