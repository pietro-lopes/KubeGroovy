package dev.uncandango.kubegroovy.plugin;

import dev.uncandango.kubegroovy.script.type.TypeInfo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Immutable, resolved data written for the type-suggestions LivePlugin.
public record TypeSuggestionCatalog(Map<String, List<String>> values, List<Delegate> delegates,
	Map<String, Integer> genericDelegates) {
	public TypeSuggestionCatalog {
		var copiedValues = new LinkedHashMap<String, List<String>>();
		values.forEach((type, suggestions) -> copiedValues.put(type, List.copyOf(suggestions)));
		values = Collections.unmodifiableMap(copiedValues);
		delegates = List.copyOf(delegates);
		genericDelegates = Collections.unmodifiableMap(new LinkedHashMap<>(genericDelegates));
	}

	public static TypeSuggestionCatalog empty() {
		return new TypeSuggestionCatalog(Map.of(), List.of(), Map.of());
	}

	public static String typeName(TypeInfo type) {
		return type.toStringFQN();
	}

	public static String className(Class<?> type) {
		var canonicalName = type.getCanonicalName();
		return canonicalName == null ? type.getName().replace('$', '.') : canonicalName;
	}

	public record Delegate(String source, String target, String prefix) {
	}
}
