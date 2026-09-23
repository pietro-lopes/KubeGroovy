package dev.uncandango.kubegroovy.script.groovy;

import java.lang.invoke.MethodHandle;

public interface MhPostProcessor {
	MethodHandle process(MethodHandle mh);
}
