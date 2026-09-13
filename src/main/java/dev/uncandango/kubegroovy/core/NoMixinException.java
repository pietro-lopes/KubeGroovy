package dev.uncandango.kubegroovy.core;

public class NoMixinException extends IllegalStateException {
	public NoMixinException() {
		super("A mixin should have implemented this method!");
	}
}
