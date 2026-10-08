package dev.uncandango.kubegroovy.plugin.builtin;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.client.KubeGroovyKeybinds;
import dev.uncandango.kubegroovy.client.LangKubeEvent;
import dev.uncandango.kubegroovy.client.ScheduledClientEvent;
import dev.uncandango.kubegroovy.event.EventGroupRegistry;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugin;
import dev.uncandango.kubegroovy.plugin.builtin.event.ClientEvents;
import dev.uncandango.kubegroovy.plugin.builtin.event.KeyBindEvents;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.GLFWInputWrapper;
import dev.uncandango.kubegroovy.script.BindingRegistry;
import dev.uncandango.kubegroovy.script.PlatformWrapper;
import dev.uncandango.kubegroovy.script.ScriptManager;
import dev.uncandango.kubegroovy.script.ScriptType;
import dev.uncandango.kubegroovy.util.ScheduledEvents;
import dev.uncandango.kubegroovy.web.LocalWebServerAPIRegistry;
import dev.uncandango.kubegroovy.web.LocalWebServerRegistry;
import dev.uncandango.kubegroovy.web.local.client.KubeGroovyClientWeb;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

public class BuiltinKubeGroovyClientPlugin implements KubeGroovyPlugin {
	@Override
	public void registerEvents(EventGroupRegistry registry) {
		registry.register(ClientEvents.GROUP);
		registry.register(KeyBindEvents.GROUP);
	}

	@Override
	public void registerBindings(BindingRegistry bindings) {
		if (bindings.type().isStartup() || bindings.type().isClient()) {
			bindings.addLazy("Client", Minecraft::getInstance);
		}

		if (bindings.type().isClient()) {
			var se = ScheduledClientEvent.EVENTS;
			bindings.add("setTimeout", new ScheduledEvents.TimeoutFunction(se, false, false));
			bindings.add("clearTimeout", new ScheduledEvents.TimeoutFunction(se, true, false));
			bindings.add("setInterval", new ScheduledEvents.TimeoutFunction(se, false, true));
			bindings.add("clearInterval", new ScheduledEvents.TimeoutFunction(se, true, true));
		}
		bindings.add("GLFWInput", GLFWInputWrapper.MAP.get());
	}

	@Override
	public void registerLocalWebServerAPIs(LocalWebServerAPIRegistry registry) {
		KubeGroovyClientWeb.registerAPIs(registry);
	}

	@Override
	public void registerLocalWebServer(LocalWebServerRegistry registry) {
		KubeGroovyClientWeb.register(registry);
	}

	@Override
	public void registerLocalWebServerWithAuth(LocalWebServerRegistry registry) {
		KubeGroovyClientWeb.registerWithAuth(registry);
	}

	@Override
	public void generateLang(LangKubeEvent event) {
		event.add(KubeGroovy.MOD_ID, "key.category.kubegroovy.kubegroovy", "KubeGroovy");
		event.add(KubeGroovy.MOD_ID, "key.kubegroovy.kubedex", "Kubedex");

		if (ModList.get().isLoaded("jade")) {
			for (var mod : PlatformWrapper.getMods().values()) {
				if (!mod.getCustomName().isEmpty()) {
					event.add(KubeGroovy.MOD_ID, "jade.modName." + mod.getId(), mod.getCustomName());
				}
			}
		}

		KubeGroovyKeybinds.generateLang(event);
	}

	@Override
	public void afterScriptsLoaded(ScriptManager manager) {
		if (manager.scriptType == ScriptType.CLIENT) {
			KubeGroovyKeybinds.triggerReload();
		}
	}
}
