package dev.uncandango.kubegroovy.script;

import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.ContextFactory;
import dev.uncandango.kubegroovy.script.bridge.TypeWrappers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// Factory producing KubeGroovyContext instances backed by GroovyShell.
public class KubeGroovyContextFactory extends ContextFactory {
	public final ScriptManager manager;
	private final TypeWrappers typeWrappers = new TypeWrappers();
	private final Map<String, Object> defaultRecordProperties = new ConcurrentHashMap<>();

	public KubeGroovyContextFactory(ScriptManager manager) {
		this.manager = manager;
	}

	@Override
	protected Context createContext() {
		return manager.scriptType.isServer() ? new KubeGroovyServerContext(this) : new KubeGroovyContext(this);
	}

	public KubeGroovyContext create() {
		return (KubeGroovyContext) createContext();
	}

	@Override
	public TypeWrappers getTypeWrappers() {
		return typeWrappers;
	}

	public void registerDefaultRecordProperties(Class<?> type, String property, Object value) {
		defaultRecordProperties.put(type.getName() + "#" + property, value);
	}
}
