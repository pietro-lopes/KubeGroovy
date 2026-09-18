package dev.uncandango.kubegroovy.testmod;

import dev.uncandango.kubegroovy.plugin.ClassFilter;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugin;
import dev.uncandango.kubegroovy.script.BindingRegistry;

public class TestRuntimePlugin implements KubeGroovyPlugin {
	@Override
	public void registerBindings(BindingRegistry bindings) {
		bindings.add("TestRuntime", TestRuntime.class);
	}

	/// Test-only class-filter allowances so script fixtures can reference
	/// wrapper targets the production deny-list otherwise blocks. The
	/// production `kubegroovy.classfilter.txt` is untouched; exact allows
	/// take precedence over prefix denies, scoped to test runs.
	/// `java.lang.Throwable` is needed because any try/catch in script
	/// bytecode makes the JVM verifier load it via the script classloader,
	/// and `java.lang.AssertionError` because native `assert` compiles to a
	/// direct throw of it.
	@Override
	public void registerClasses(ClassFilter filter) {
		filter.allow("java.io.File");
		filter.allow("java.nio.file.Path");
		filter.allow("java.lang.Throwable");
		filter.allow("java.lang.AssertionError");
		filter.allow("dev.uncandango.kubegroovy.core.PlayerSelector");
		filter.allow("dev.uncandango.kubegroovy.script.bridge.BaseFunction");
	}
}
