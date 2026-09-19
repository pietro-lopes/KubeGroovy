package dev.uncandango.kubegroovy.script.groovy;

import dev.uncandango.kubegroovy.script.WithScriptContext;
import dev.uncandango.kubegroovy.script.bridge.Context;
import groovy.lang.Script;
import org.jspecify.annotations.Nullable;

/** Base class for all KubeGroovy scripts. Exposes console/binding helpers. */
public abstract class KubeGroovyScript extends Script implements WithScriptContext {
	private final Context context = Context.getCurrent();

	@Override
	public Context cx() {
		return context;
	}

	public void log(Object o) {
		var binding = getBinding();
		if (binding != null && binding.hasVariable("console")) {
			var console = binding.getVariable("console");
			if (console instanceof dev.uncandango.kubegroovy.script.ConsoleGY gy) {
				gy.info(o);
				return;
			}
		}
		System.out.println(o);
	}
}
