package dev.uncandango.kubegroovy.script.groovy;

import groovy.lang.Script;

/** Base class for all KubeGroovy scripts. Exposes console/binding helpers. */
public abstract class KubeGroovyScript extends Script {
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
