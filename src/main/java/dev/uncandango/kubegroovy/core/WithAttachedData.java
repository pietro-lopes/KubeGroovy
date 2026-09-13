package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.util.AttachedData;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;

public interface WithAttachedData<T> extends MessageSenderKGY {
	@RemapForGY("getData")
	AttachedData<T> kgy$getData();
}