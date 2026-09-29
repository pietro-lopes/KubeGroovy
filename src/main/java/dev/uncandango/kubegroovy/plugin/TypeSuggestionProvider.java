package dev.uncandango.kubegroovy.plugin;

import dev.uncandango.kubegroovy.script.KubeGroovyContext;
import dev.uncandango.kubegroovy.script.type.TypeInfo;

import java.util.stream.Stream;

/// Produces strings that can be offered when a script expression expects a type.
@FunctionalInterface
public interface TypeSuggestionProvider {
	Stream<String> getSuggestions(KubeGroovyContext cx, TypeInfo typeInfo);
}
