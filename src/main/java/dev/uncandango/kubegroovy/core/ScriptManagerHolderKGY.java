package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.server.ServerScriptManager;
import org.jspecify.annotations.NullUnmarked;

@NullUnmarked // safe to call from both spots where we need it
public interface ScriptManagerHolderKGY {
	void kgy$setScriptManager(ServerScriptManager manager);

	ServerScriptManager kgy$getScriptManager();
}
