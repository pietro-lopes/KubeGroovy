package dev.uncandango.kubegroovy.script.bridge;

public class EvaluatorException extends RhinoException {
	public EvaluatorException(String message) {
		super(message);
	}

	public EvaluatorException(String message, Throwable cause) {
		super(message, cause);
	}

	public String details() {
		return getMessage();
	}
}
