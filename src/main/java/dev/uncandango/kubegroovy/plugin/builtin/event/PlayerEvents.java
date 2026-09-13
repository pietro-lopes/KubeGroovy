package dev.uncandango.kubegroovy.plugin.builtin.event;

import dev.uncandango.kubegroovy.event.EventGroup;
import dev.uncandango.kubegroovy.event.EventHandler;
import dev.uncandango.kubegroovy.event.EventTargetType;
import dev.uncandango.kubegroovy.event.TargetedEventHandler;
import dev.uncandango.kubegroovy.player.ChestKubeEvent;
import dev.uncandango.kubegroovy.player.InventoryChangedKubeEvent;
import dev.uncandango.kubegroovy.player.InventoryKubeEvent;
import dev.uncandango.kubegroovy.player.PlayerAdvancementKubeEvent;
import dev.uncandango.kubegroovy.player.PlayerChatReceivedKubeEvent;
import dev.uncandango.kubegroovy.player.PlayerClonedKubeEvent;
import dev.uncandango.kubegroovy.player.PlayerRespawnedKubeEvent;
import dev.uncandango.kubegroovy.player.SimplePlayerKubeEvent;
import dev.uncandango.kubegroovy.player.StageChangedEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;

public interface PlayerEvents {
	EventTargetType<ResourceKey<MenuType<?>>> MENU_TARGET = EventTargetType.registryKey(Registries.MENU, MenuType.class);

	EventGroup GROUP = EventGroup.of("PlayerEvents");
	EventHandler LOGGED_IN = GROUP.server("loggedIn", () -> SimplePlayerKubeEvent.class);
	EventHandler LOGGED_OUT = GROUP.server("loggedOut", () -> SimplePlayerKubeEvent.class);
	EventHandler CLONED = GROUP.server("cloned", () -> PlayerClonedKubeEvent.class);
	EventHandler RESPAWNED = GROUP.server("respawned", () -> PlayerRespawnedKubeEvent.class);
	EventHandler TICK = GROUP.common("tick", () -> SimplePlayerKubeEvent.class);
	EventHandler DECORATE_CHAT = GROUP.server("decorateChat", () -> PlayerChatReceivedKubeEvent.class).hasResult();
	EventHandler CHAT = GROUP.server("chat", () -> PlayerChatReceivedKubeEvent.class).hasResult();
	TargetedEventHandler<Identifier> ADVANCEMENT = GROUP.server("advancement", () -> PlayerAdvancementKubeEvent.class).hasResult().supportsTarget(EventTargetType.ID);
	TargetedEventHandler<ResourceKey<MenuType<?>>> INVENTORY_OPENED = GROUP.common("inventoryOpened", () -> InventoryKubeEvent.class).supportsTarget(MENU_TARGET);
	TargetedEventHandler<ResourceKey<MenuType<?>>> INVENTORY_CLOSED = GROUP.common("inventoryClosed", () -> InventoryKubeEvent.class).supportsTarget(MENU_TARGET);
	TargetedEventHandler<ResourceKey<Item>> INVENTORY_CHANGED = GROUP.common("inventoryChanged", () -> InventoryChangedKubeEvent.class).supportsTarget(ItemEvents.TARGET);
	TargetedEventHandler<ResourceKey<MenuType<?>>> CHEST_OPENED = GROUP.common("chestOpened", () -> ChestKubeEvent.class).supportsTarget(MENU_TARGET);
	TargetedEventHandler<ResourceKey<MenuType<?>>> CHEST_CLOSED = GROUP.common("chestClosed", () -> ChestKubeEvent.class).supportsTarget(MENU_TARGET);
	TargetedEventHandler<String> STAGE_ADDED = GROUP.common("stageAdded", () -> StageChangedEvent.class).supportsTarget(EventTargetType.STRING);
	TargetedEventHandler<String> STAGE_REMOVED = GROUP.common("stageRemoved", () -> StageChangedEvent.class).supportsTarget(EventTargetType.STRING);
}
