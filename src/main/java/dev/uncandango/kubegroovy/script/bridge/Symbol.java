package dev.uncandango.kubegroovy.script.bridge;

/** Legacy-compat symbol marker. No-op for Groovy. */
public final class Symbol {
	public static final Symbol ITERATOR = new Symbol("iterator");

	private final String name;

	public Symbol(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return "Symbol(" + name + ")";
	}
}
