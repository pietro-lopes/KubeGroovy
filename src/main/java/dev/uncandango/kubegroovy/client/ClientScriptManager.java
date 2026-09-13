package dev.uncandango.kubegroovy.client;

import dev.uncandango.kubegroovy.script.ScriptManager;
import dev.uncandango.kubegroovy.script.ScriptType;

public class ClientScriptManager extends ScriptManager {
	public ClientScriptManager() {
		super(ScriptType.CLIENT);
	}
}
