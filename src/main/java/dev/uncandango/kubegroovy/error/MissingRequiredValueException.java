package dev.uncandango.kubegroovy.error;

public class MissingRequiredValueException extends KubeRuntimeException {
	public MissingRequiredValueException() {
		super("Missing required value");
	}
}
