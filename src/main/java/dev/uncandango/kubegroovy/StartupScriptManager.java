package dev.uncandango.kubegroovy;

import dev.uncandango.kubegroovy.script.ScriptManager;
import dev.uncandango.kubegroovy.script.ScriptType;
import net.neoforged.fml.loading.FMLLoader;

public class StartupScriptManager extends ScriptManager {
	public StartupScriptManager() {
		super(ScriptType.STARTUP);
	}

	@Override
	public void loadFromDirectory() {
		super.loadFromDirectory();

		if (FMLLoader.getCurrent().getDist().isDedicatedServer()) {
			loadPackFromDirectory(KubeGroovyPaths.LOCAL_STARTUP_SCRIPTS, "local startup", true);
		}
	}
}
