package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.server.ServerScriptManager;

public interface ReloadableServerResourceHolderKGY {
	default void kgy$setResources(ReloadableServerResourcesKGY resources) {
		throw new NoMixinException();
	}

	default ServerScriptManager kgy$getServerScriptManager() {
		throw new NoMixinException();
	}
}