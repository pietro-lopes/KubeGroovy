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

	public static int toInt32(Object o) {
		var cx = Context.getCurrent();
		if (o instanceof Number n) {
			return n.intValue();
		}
		try {
			return Integer.parseInt(String.valueOf(o));
		} catch (Exception e) {
			return 0;
		}
	}

	public static String escapeAndWrapString(String s) {
		if (s == null) {
			return "null";
		}
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
	}

}
