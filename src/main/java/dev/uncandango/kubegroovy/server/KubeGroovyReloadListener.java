package dev.uncandango.kubegroovy.server;

import dev.uncandango.kubegroovy.plugin.builtin.event.ServerEvents;
import dev.uncandango.kubegroovy.recipe.AfterRecipesLoadedKubeEvent;
import dev.uncandango.kubegroovy.script.ScriptType;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public record KubeGroovyReloadListener(ReloadableServerResources resources) implements ResourceManagerReloadListener {
	@Override
	public void onResourceManagerReload(ResourceManager resourceManager) {
		if (ServerEvents.RECIPES_AFTER_LOADED.hasListeners()) {
			ServerEvents.RECIPES_AFTER_LOADED.post(ScriptType.SERVER, new AfterRecipesLoadedKubeEvent(resources));
		}
	}
}
