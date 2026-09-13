package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.ScriptManagerHolderKGY;
import dev.uncandango.kubegroovy.server.ServerScriptManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MultiPackResourceManager.class)
public class MultiPackResourceManagerMixin implements ScriptManagerHolderKGY {
	@Unique
	private ServerScriptManager kgy$pendingServerScriptManager;

	@Override
	public void kgy$setScriptManager(ServerScriptManager manager) {
		kgy$pendingServerScriptManager = manager;
	}

	@Override
	public ServerScriptManager kgy$getScriptManager() {
		return kgy$pendingServerScriptManager;
	}
}
