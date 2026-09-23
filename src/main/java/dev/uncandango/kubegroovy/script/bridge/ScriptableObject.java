//package dev.uncandango.kubegroovy.script.bridge;
//
//import java.util.HashMap;
//
///** Minimal legacy-compat scope object. Backed by a map for now. */
//public class ScriptableObject extends HashMap<String, Object> implements Scriptable {
//	public static final int EMPTY = 0;
//
//	public static Object getFunctionPrototype(Scriptable scope, Context cx) {
//		return null;
//	}
//
//	public void delete(Context cx, String name) {
//		remove(name);
//	}
//
//	public void setGetterOrSetter(Context cx, String name, int index, BaseFunction getter, boolean setter) {
//		// No-op: Groovy port resolves bindings eagerly. Kept for source compat.
//	}
//
//	public void setAttributes(Context cx, String name, int attrs) {
//	}
//}
