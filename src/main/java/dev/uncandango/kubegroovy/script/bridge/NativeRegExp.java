package dev.uncandango.kubegroovy.script.bridge;

import java.util.regex.Pattern;

/** Legacy-compat regexp wrapper. Backed by java.util.regex.Pattern. */
public class NativeRegExp implements Scriptable, Wrapper {
	private final Pattern pattern;

	public NativeRegExp(String source) {
		this.pattern = Pattern.compile(source);
	}

	public NativeRegExp(Pattern pattern) {
		this.pattern = pattern;
	}

	public Pattern getPattern() {
		return pattern;
	}

	@Override
	public Object unwrap() {
		return pattern;
	}

	@Override
	public String toString() {
		return pattern.pattern();
	}
}
