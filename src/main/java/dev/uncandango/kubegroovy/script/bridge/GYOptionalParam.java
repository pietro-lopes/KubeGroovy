package dev.uncandango.kubegroovy.script.bridge;

public record GYOptionalParam(String name, TypeInfo type, boolean optional) {
	public GYOptionalParam(String name, TypeInfo type) {
		this(name, type, false);
	}
}
