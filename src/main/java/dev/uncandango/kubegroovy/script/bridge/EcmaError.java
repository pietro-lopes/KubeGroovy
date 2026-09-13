package dev.uncandango.kubegroovy.script.bridge;

public class EcmaError extends RhinoException {
	public EcmaError(String message) {
		super(message);
	}
}
