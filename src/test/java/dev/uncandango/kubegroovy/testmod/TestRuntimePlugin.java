package dev.uncandango.kubegroovy.testmod;

import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugin;
import dev.uncandango.kubegroovy.script.BindingRegistry;

public class TestRuntimePlugin implements KubeGroovyPlugin {
	@Override
	public void registerBindings(BindingRegistry bindings) {
		bindings.add("TestRuntime", TestRuntime.class);
	}
}
