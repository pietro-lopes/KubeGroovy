package dev.uncandango.kubegroovy.plugin.builtin.event;

import dev.uncandango.kubegroovy.event.EventGroup;
import dev.uncandango.kubegroovy.event.EventHandler;
import dev.uncandango.kubegroovy.event.EventTargetType;
import dev.uncandango.kubegroovy.event.KubeStartupEvent;
import dev.uncandango.kubegroovy.event.TargetedEventHandler;
import dev.uncandango.kubegroovy.item.creativetab.CreativeTabKubeEvent;
import dev.uncandango.kubegroovy.registry.RegistryKubeEvent;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public interface StartupEvents {
	EventGroup GROUP = EventGroup.of("StartupEvents");

	EventHandler INIT = GROUP.startup("init", () -> KubeStartupEvent.class);
	EventHandler POST_INIT = GROUP.startup("postInit", () -> KubeStartupEvent.class);
	TargetedEventHandler<ResourceKey<? extends Registry<?>>> REGISTRY = GROUP.startup("registry", () -> RegistryKubeEvent.class).requiredTarget(EventTargetType.REGISTRY);
	TargetedEventHandler<Identifier> MODIFY_CREATIVE_TAB = GROUP.startup("modifyCreativeTab", () -> CreativeTabKubeEvent.class).requiredTarget(EventTargetType.ID);
}
