package dev.uncandango.kubegroovy.script;

import dev.uncandango.kubegroovy.script.type.TypeInfo;

public interface TypeDescriptionRegistry {
	ScriptType scriptType();

	void register(Class<?> target, TypeInfo typeInfo);
}
