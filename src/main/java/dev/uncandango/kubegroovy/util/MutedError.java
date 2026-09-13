package dev.uncandango.kubegroovy.util;

public interface MutedError {
	default boolean isMuted() {
		return true;
	}
}
