package dev.uncandango.kubegroovy.script.bridge;

/** Legacy-compat base script exception. */
public class RhinoException extends RuntimeException {
	public RhinoException(String message) {
		super(message);
	}

	public RhinoException(String message, Throwable cause) {
		super(message, cause);
	}

	public String lineSource() {
		return null;
	}

	public int lineNumber() {
		return -1;
	}

	public ScriptStackElement[] getScriptStack() {
		return new ScriptStackElement[0];
	}

	public static class ScriptStackElement {
		public String fileName;
		public int lineNumber;

		public ScriptStackElement(String fileName, int lineNumber) {
			this.fileName = fileName;
			this.lineNumber = lineNumber;
		}
	}
}
