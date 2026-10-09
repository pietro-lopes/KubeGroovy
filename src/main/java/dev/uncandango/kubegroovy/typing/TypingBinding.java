package dev.uncandango.kubegroovy.typing;

import dev.uncandango.kubegroovy.event.EventGroupWrapper;
import dev.uncandango.kubegroovy.script.ScriptType;

import java.lang.reflect.Modifier;

/**
 * A small immutable description of a script binding.  Values themselves are
 * intentionally not retained: a binding can be a live Minecraft object and
 * must never be replayed or serialized by the typing generator.
 */
public record TypingBinding(String name, String typeName, boolean classValue, boolean eventGroup, ScriptType scriptType) {
	public TypingBinding {
		name = name == null ? "" : name;
		typeName = typeName == null || typeName.isBlank() ? Object.class.getName() : typeName;
	}

	public static TypingBinding describe(String name, Object value, ScriptType scriptType) {
		if (name == null || value == null || scriptType == null) {
			return null;
		}

		if (value instanceof Class<?> type) {
			return new TypingBinding(name, type.getName(), true, false, scriptType);
		}

		boolean eventGroup = value instanceof EventGroupWrapper;
		Class<?> type = publicType(value.getClass());
		return new TypingBinding(name, type.getName(), false, eventGroup, scriptType);
	}

	private static Class<?> publicType(Class<?> type) {
		if (Modifier.isPublic(type.getModifiers())) {
			return type;
		}
		for (var iface : type.getInterfaces()) {
			if (Modifier.isPublic(iface.getModifiers())) {
				return iface;
			}
		}
		var parent = type.getSuperclass();
		while (parent != null && !Modifier.isPublic(parent.getModifiers())) {
			parent = parent.getSuperclass();
		}
		return parent == null ? Object.class : parent;
	}
}
