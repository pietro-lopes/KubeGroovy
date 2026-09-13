package dev.uncandango.kubegroovy.script.bridge;

public class WrappedException extends RhinoException {
	private final Throwable wrapped;

	public WrappedException(Throwable wrapped) {
		super(String.valueOf(wrapped), wrapped);
		this.wrapped = wrapped;
	}

	public Throwable getWrappedException() {
		return wrapped;
	}
}
