package dev.uncandango.kubegroovy.script.bridge;

/** Legacy-compat runtime helpers. Mostly no-ops delegating to plain Java. */
public final class ScriptRuntime {
	private ScriptRuntime() {
	}

	public static String toString(Object o) {
		return String.valueOf(o);
	}

	public static boolean toBoolean(Object o) {
		if (o == null) {
			return false;
		}
		if (o instanceof Boolean b) {
			return b;
		}
		if (o instanceof Number n) {
			return n.doubleValue() != 0;
		}
		if (o instanceof CharSequence s) {
			return s.length() > 0;
		}
		return true;
	}

	public static int toInt32(Context cx, Object o) {
		if (o instanceof Number n) {
			return n.intValue();
		}
		try {
			return Integer.parseInt(String.valueOf(Wrapper.unwrapped(o)));
		} catch (Exception e) {
			return 0;
		}
	}

	public static int toInt32(Object o) {
		return toInt32(null, o);
	}

	public static String escapeAndWrapString(String s) {
		if (s == null) {
			return "null";
		}
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
	}

	public static Object doTopCall(Object callable, Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
		if (callable instanceof BaseFunction fn) {
			return fn.call(cx, scope, thisObj, args);
		}
		return null;
	}
}
