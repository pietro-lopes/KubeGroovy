package dev.uncandango.kubegroovy.event;

import dev.uncandango.kubegroovy.script.ScriptType;
import dev.uncandango.kubegroovy.script.bridge.BaseFunction;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.GeneratedClosure;

import java.util.HashMap;
import java.util.Set;

public class EventGroupWrapper extends HashMap<String, EventHandler> {
	private final ScriptType scriptType;
	private final EventGroup group;

	public EventGroupWrapper(ScriptType scriptType, EventGroup group) {
		this.scriptType = scriptType;
		this.group = group;
	}

	@Override
	public EventHandler get(Object key) {
		var keyString = String.valueOf(key);
		var handler = group.getHandlers().get(keyString);

		if (handler == null) {
			scriptType.console.error("Unknown event '%s.%s'!".formatted(group.name, keyString));
			throw new IllegalArgumentException("Unknown event '%s.%s'!".formatted(group.name, keyString));
		}



		return handler;
	}

	@Override
	public boolean containsKey(Object key) {
		return true;
	}

	@Override
	public Set<String> keySet() {
		return group.getHandlers().keySet();
	}
}
