package dev.uncandango.kubegroovy.error;

public class MissingContextException extends KubeRuntimeException {
	public MissingContextException() {
		super("Missing context!");
	}
	public MissingContextException(Throwable cause) {
		super("Missing context!", cause);
	}
}
