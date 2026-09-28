package dev.uncandango.kubegroovy.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal generic type-text parser. This is the executable spec for the
 * LivePlugin events provider's in-IDE mirror (see that script's
 * {@code PARSER-BEGIN} section): splits {@code FQN}, {@code FQN<A, B>}
 * (arbitrarily nested) and trailing {@code []} into a tree the provider
 * assembles into real PsiTypes. Building inside-out from the tree — instead
 * of one opaque fragment — lets the provider reference the synthetic
 * method's own type variables by identity, which is what call-site type
 * inference ({@code T = Block}) needs. Returns null on anything outside
 * that grammar (wildcards, intersections, malformed input); callers fall
 * back to fragment parsing, never fail.
 */
public record TypeTree(String name, List<TypeTree> args, int arrays) {
	public TypeTree {
		if (name == null || name.isBlank()) throw new IllegalArgumentException("TypeTree needs a name");
		args = List.copyOf(args == null ? List.of() : args);
		if (arrays < 0) throw new IllegalArgumentException("TypeTree arrays cannot be negative");
	}

	public static TypeTree parse(String text) {
		if (text == null) return null;
		var parser = new Parser(text);
		var tree = parser.parseType();
		if (tree == null) return null;
		parser.whitespace();
		return parser.atEnd() ? tree : null;
	}

	private static final class Parser {
		private final String text;
		private int index;

		private Parser(String text) {
			this.text = text;
			this.index = 0;
		}

		private boolean atEnd() {
			return index >= text.length();
		}

		private char peek() {
			return atEnd() ? 0 : text.charAt(index);
		}

		private char peekNext() {
			return index + 1 >= text.length() ? 0 : text.charAt(index + 1);
		}

		private void whitespace() {
			while (!atEnd() && Character.isWhitespace(text.charAt(index))) index++;
		}

		private TypeTree parseType() {
			whitespace();
			String name = parseName();
			if (name == null) return null;
			whitespace();
			List<TypeTree> args = List.of();
			if (peek() == '<') {
				index++;
				var collected = new ArrayList<TypeTree>();
				var first = parseType();
				if (first == null) return null;
				collected.add(first);
				whitespace();
				while (peek() == ',') {
					index++;
					var next = parseType();
					if (next == null) return null;
					collected.add(next);
					whitespace();
				}
				if (peek() != '>') return null;
				index++;
				args = collected;
			}
			int arrays = 0;
			for (;;) {
				whitespace();
				if (peek() == '[' && peekNext() == ']') {
					arrays++;
					index += 2;
				} else {
					break;
				}
			}
			return new TypeTree(name, args, arrays);
		}

		private String parseName() {
			int start = index;
			while (!atEnd()) {
				char current = text.charAt(index);
				if (Character.isJavaIdentifierPart(current) || current == '.') {
					index++;
				} else {
					break;
				}
			}
			if (index == start) return null;
			String name = text.substring(start, index);
			for (var segment : name.split("\\.", -1)) {
				if (segment.isEmpty() || !Character.isJavaIdentifierStart(segment.charAt(0))) return null;
				for (int i = 1; i < segment.length(); i++) {
					if (!Character.isJavaIdentifierPart(segment.charAt(i))) return null;
				}
			}
			return name;
		}
	}
}
