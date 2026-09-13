package dev.uncandango.kubegroovy.error;

public class EmptyTagTargetException extends KubeRuntimeException {
	public EmptyTagTargetException(String message) {
		super(message);
	}
}
