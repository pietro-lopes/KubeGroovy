package dev.uncandango.kubegroovy.player;

import dev.uncandango.kubegroovy.CommonProperties;
import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.net.KubeGroovyNet;
import dev.uncandango.kubegroovy.plugin.builtin.event.PlayerEvents;
import dev.uncandango.kubegroovy.script.ConsoleGY;
import dev.uncandango.kubegroovy.script.ScriptType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = KubeGroovy.MOD_ID)
public class KubeGroovyPlayerEventHandler {
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void datapackSync(OnDatapackSyncEvent event) {
		var payload = event.getPlayerList().getServer().getServerResources().managers().kgy$getServerScriptManager().serverData;

		if (payload != null) {
			event.getRelevantPlayers().forEach(player -> KubeGroovyNet.safeSendToPlayer(player, payload));
		}
	}

	@SubscribeEvent
	public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			if (PlayerEvents.LOGGED_IN.hasListeners()) {
				PlayerEvents.LOGGED_IN.post(ScriptType.SERVER, new SimplePlayerKubeEvent(player));
			}

			player.inventoryMenu.addSlotListener(player.kgy$getInventoryChangeListener());

			if (!ConsoleGY.SERVER.errors.isEmpty() && !CommonProperties.get().hideServerScriptErrors) {
				player.sendSystemMessage(ConsoleGY.SERVER.errorsComponent("/kubegroovy errors server"), false);
			}

			player.kgy$getStages().sync();
		}
	}

	@SubscribeEvent
	public static void cloned(PlayerEvent.Clone event) {
		if (event.getOriginal() instanceof ServerPlayer oldPlayer && event.getEntity() instanceof ServerPlayer newPlayer) {
			newPlayer.kgy$setRawPersistentData(oldPlayer.kgy$getRawPersistentData());
			newPlayer.inventoryMenu.addSlotListener(newPlayer.kgy$getInventoryChangeListener()); // move this to respawn?

			if (PlayerEvents.CLONED.hasListeners()) {
				PlayerEvents.CLONED.post(ScriptType.SERVER, new PlayerClonedKubeEvent(newPlayer, oldPlayer, !event.isWasDeath()));
			}
		}
	}

	@SubscribeEvent
	public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			if (PlayerEvents.RESPAWNED.hasListeners()) {
				PlayerEvents.RESPAWNED.post(ScriptType.SERVER, new PlayerRespawnedKubeEvent(player, event.isEndConquered()));
			}

			player.kgy$getStages().sync();
		}
	}

	@SubscribeEvent
	public static void loggedOut(PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			// restore player's inv before they're saved because they may have had a chest gui open
			player.kgy$restoreCapturedInventory();

			if (PlayerEvents.LOGGED_OUT.hasListeners()) {
				PlayerEvents.LOGGED_OUT.post(ScriptType.SERVER, new SimplePlayerKubeEvent(player));
			}
		}
	}

	@SubscribeEvent
	public static void tick(PlayerTickEvent.Post event) {
		if (PlayerEvents.TICK.hasListeners() && event.getEntity() instanceof ServerPlayer player) {
			PlayerEvents.TICK.post(player, new SimplePlayerKubeEvent(player));
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void chatDecorate(ServerChatEvent event) {
		if (PlayerEvents.DECORATE_CHAT.hasListeners()) {
			PlayerEvents.DECORATE_CHAT.post(ScriptType.SERVER, new PlayerChatReceivedKubeEvent(event)).applyCancel(event);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void chatReceived(ServerChatEvent event) {
		if (PlayerEvents.CHAT.hasListeners()) {
			PlayerEvents.CHAT.post(ScriptType.SERVER, new PlayerChatReceivedKubeEvent(event)).applyCancel(event);
		}
	}

	@SubscribeEvent
	public static void advancement(AdvancementEvent.AdvancementEarnEvent event) {
		var id = event.getAdvancement().id();

		if (PlayerEvents.ADVANCEMENT.hasListeners(id) && event.getEntity() instanceof ServerPlayer player) {
			PlayerEvents.ADVANCEMENT.post(new PlayerAdvancementKubeEvent(player, player.server.kgy$getAdvancement(id)), id);
		}
	}

	@SubscribeEvent
	public static void inventoryOpened(PlayerContainerEvent.Open event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			var menu = event.getContainer();

			if (!(menu instanceof InventoryMenu)) {
				menu.addSlotListener(player.kgy$getInventoryChangeListener());
			}

			ResourceKey<MenuType<?>> key;

			try {
				key = menu.getType().kgy$getKey();
			} catch (Exception ex) {
				return;
			}

			if (key != null) {
				if (PlayerEvents.INVENTORY_OPENED.hasListeners(key)) {
					PlayerEvents.INVENTORY_OPENED.post(player, key, new InventoryKubeEvent(player, menu));
				}

				if (menu instanceof ChestMenu && PlayerEvents.CHEST_OPENED.hasListeners(key)) {
					PlayerEvents.CHEST_OPENED.post(player, key, new ChestKubeEvent(player, menu));
				}
			}
		}
	}

	@SubscribeEvent
	public static void inventoryClosed(PlayerContainerEvent.Close event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			var menu = event.getContainer();

			ResourceKey<MenuType<?>> key;

			try {
				key = menu.getType().kgy$getKey();
			} catch (Exception ex) {
				return;
			}

			if (key != null) {
				if (PlayerEvents.INVENTORY_CLOSED.hasListeners(key)) {
					PlayerEvents.INVENTORY_CLOSED.post(player, key, new InventoryKubeEvent(player, menu));
				}

				if (menu instanceof ChestMenu && PlayerEvents.CHEST_CLOSED.hasListeners(key)) {
					PlayerEvents.CHEST_CLOSED.post(player, key, new ChestKubeEvent(player, menu));
				}
			}
		}
	}

	@SubscribeEvent
	public static void dimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
		try {
			event.getEntity().kgy$getStages().sync();
		} catch (Exception ignored) {
		}
	}
}