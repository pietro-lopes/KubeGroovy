package dev.uncandango.kubegroovy.plugin.builtin.event;

import dev.uncandango.kubegroovy.client.AtlasSpriteRegistryKubeEvent;
import dev.uncandango.kubegroovy.client.BlockEntityRendererRegistryKubeEvent;
import dev.uncandango.kubegroovy.client.ClientPlayerKubeEvent;
import dev.uncandango.kubegroovy.client.DebugInfoKubeEvent;
import dev.uncandango.kubegroovy.client.EntityRendererRegistryKubeEvent;
import dev.uncandango.kubegroovy.client.LangKubeEvent;
import dev.uncandango.kubegroovy.client.MenuScreenRegistryKubeEvent;
import dev.uncandango.kubegroovy.client.ParticleProviderRegistryKubeEvent;
import dev.uncandango.kubegroovy.event.EventGroup;
import dev.uncandango.kubegroovy.event.EventHandler;
import dev.uncandango.kubegroovy.event.EventTargetType;
import dev.uncandango.kubegroovy.event.TargetedEventHandler;
import dev.uncandango.kubegroovy.generator.KubeAssetGenerator;
import dev.uncandango.kubegroovy.script.data.GeneratedDataStage;
import net.minecraft.resources.Identifier;

public interface ClientEvents {
	EventGroup GROUP = EventGroup.of("ClientEvents");

	TargetedEventHandler<GeneratedDataStage> GENERATE_ASSETS = GROUP.client("generateAssets", () -> KubeAssetGenerator.class).requiredTarget(GeneratedDataStage.TARGET);
	EventHandler BLOCK_ENTITY_RENDERER_REGISTRY = GROUP.startup("blockEntityRendererRegistry", () -> BlockEntityRendererRegistryKubeEvent.class);
	EventHandler ENTITY_RENDERER_REGISTRY = GROUP.startup("entityRendererRegistry", () -> EntityRendererRegistryKubeEvent.class);
	EventHandler MENU_SCREEN_REGISTRY = GROUP.startup("menuScreenRegistry", () -> MenuScreenRegistryKubeEvent.class);
	EventHandler LOGGED_IN = GROUP.client("loggedIn", () -> ClientPlayerKubeEvent.class);
	EventHandler LOGGED_OUT = GROUP.client("loggedOut", () -> ClientPlayerKubeEvent.class);
	EventHandler TICK = GROUP.client("tick", () -> ClientPlayerKubeEvent.class);
	EventHandler DEBUG_LEFT = GROUP.client("leftDebugInfo", () -> DebugInfoKubeEvent.class);
	EventHandler DEBUG_RIGHT = GROUP.client("rightDebugInfo", () -> DebugInfoKubeEvent.class);
	TargetedEventHandler<Identifier> ATLAS_SPRITE_REGISTRY = GROUP.client("atlasSpriteRegistry", () -> AtlasSpriteRegistryKubeEvent.class).requiredTarget(EventTargetType.ID);
	TargetedEventHandler<String> LANG = GROUP.client("lang", () -> LangKubeEvent.class).requiredTarget(EventTargetType.STRING);
	EventHandler PARTICLE_PROVIDER_REGISTRY = GROUP.client("particleProviderRegistry", () -> ParticleProviderRegistryKubeEvent.class);
	// EventHandler HIGHLIGHT = GROUP.client("highlight", () -> HighlightKubeEvent.class);
}
