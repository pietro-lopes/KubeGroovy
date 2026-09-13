package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.gui.KubeGroovyGUI;
import dev.uncandango.kubegroovy.gui.KubeGroovyMenu;
import dev.uncandango.kubegroovy.gui.chest.ChestMenuData;
import dev.uncandango.kubegroovy.gui.chest.CustomChestMenu;
import dev.uncandango.kubegroovy.level.LevelBlock;
import dev.uncandango.kubegroovy.net.KubeGroovyNet;
import dev.uncandango.kubegroovy.net.NotificationPayload;
import dev.uncandango.kubegroovy.net.SendDataFromServerPayload;
import dev.uncandango.kubegroovy.net.SetActivePostShaderPayload;
import dev.uncandango.kubegroovy.player.PlayerStatsGY;
import dev.uncandango.kubegroovy.typings.Info;
import dev.uncandango.kubegroovy.typings.Param;
import dev.uncandango.kubegroovy.typings.ThisIs;
import dev.uncandango.kubegroovy.util.NotificationToastData;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelData;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Consumer;

@RemapPrefixForGY("kgy$")
public interface ServerPlayerKGY extends PlayerKGY {
	@Override
	@HideFromGY
	default ServerPlayer kgy$self() {
		return (ServerPlayer) this;
	}

	@ThisIs(ServerPlayer.class)
	@Info("Checks if the entity is a server-side player.")
	default boolean kgy$isServerPlayer() {
		return true;
	}

	@Override
	default void kgy$sendData(String channel, @Nullable CompoundTag data) {
		if (!channel.isEmpty()) {
			KubeGroovyNet.safeSendToPlayer(kgy$self(), new SendDataFromServerPayload(channel, data));
		}
	}

	@Override
	default PlayerStatsGY kgy$getStats() {
		return new PlayerStatsGY(kgy$self(), kgy$self().getStats());
	}

	@Override
	@Info("Checks, whether the player is currently mining a block.")
	default boolean kgy$isMiningBlock() {
		return kgy$self().gameMode.isDestroyingBlock;
	}

	@Override
	default void kgy$setPositionAndRotation(double x, double y, double z, float yaw, float pitch) {
		PlayerKGY.super.kgy$setPositionAndRotation(x, y, z, yaw, pitch);
		kgy$self().connection.teleport(x, y, z, yaw, pitch);
	}

	@Info(value = """
		Switches the player's gamemode between Creative and Survival.
		To change the player's gamemode to a mode other than Creative or Survival, use `setGameMode`.
		""", params = {
		@Param(name = "mode", value = """
			`true` to change the player's gamemode to Creative.
			`false` to change the player's gamemode to Survival.
			""")
	})
	default void kgy$setCreativeMode(boolean mode) {
		kgy$self().setGameMode(mode ? GameType.CREATIVE : GameType.SURVIVAL);
	}

	@Info("Checks, whether the player is a server operator.")
	default boolean kgy$isOp() {
		return kgy$self().server.getPlayerList().isOp(kgy$self().nameAndId());
	}

	@Info(value = "Kicks the player from the server with the provided reason.", params = {
		@Param(name = "reason", value = "A text component, containing the kick reason. It may be a string, which will be implicitly wrapped into a text component.")
	})
	default void kgy$kick(Component reason) {
		kgy$self().connection.disconnect(reason);
	}

	@Info("Kicks the player from the server with a generic reason.")
	default void kgy$kick() {
		kgy$kick(Component.translatable("multiplayer.disconnect.kicked"));
	}

	@Info(value = "Bans the player from the server.", params = {
		@Param(name = "banner", value = "A string, that specifies who/what banned the player."),
		@Param(name = "reason", value = "A string, that contains the ban reason."),
		@Param(name = "banDuration", value = "Duration of a ban. Negative durations will result in a 10-year ban.")
	})
	default void kgy$ban(String banner, String reason, Duration banDuration) {
		final long TEN_YEARS_SECONDS = 315569520;
		var start = Instant.now();
		var end = start.plus(banDuration);

		var userlistbansentry = new UserBanListEntry(kgy$self().nameAndId(), Date.from(start), banner, Date.from(start.isBefore(end) ? end : start.plus(Duration.ofSeconds(TEN_YEARS_SECONDS))), reason);
		kgy$self().server.getPlayerList().getBans().add(userlistbansentry);
		kgy$kick(Component.translatable("multiplayer.disconnect.banned"));
	}

	default boolean kgy$isAdvancementDone(Identifier id) {
		var a = kgy$self().server.kgy$getAdvancement(id);
		return a != null && kgy$self().getAdvancements().getOrStartProgress(a.holder()).isDone();
	}

	default void kgy$unlockAdvancement(Identifier id) {
		var a = kgy$self().server.kgy$getAdvancement(id);

		if (a != null) {
			var advancementprogress = kgy$self().getAdvancements().getOrStartProgress(a.holder());

			for (var s : advancementprogress.getRemainingCriteria()) {
				kgy$self().getAdvancements().award(a.holder(), s);
			}
		}
	}

	default void kgy$revokeAdvancement(Identifier id) {
		var a = kgy$self().server.kgy$getAdvancement(id);

		if (a != null) {
			var advancementprogress = kgy$self().getAdvancements().getOrStartProgress(a.holder());

			if (advancementprogress.hasProgress()) {
				for (var s : advancementprogress.getCompletedCriteria()) {
					kgy$self().getAdvancements().revoke(a.holder(), s);
				}
			}
		}
	}

	@Override
	default void kgy$setSelectedSlot(int index) {
		var p = kgy$getSelectedSlot();
		PlayerKGY.super.kgy$setSelectedSlot(index);
		var n = kgy$getSelectedSlot();

		if (p != n && kgy$self().connection != null) {
			kgy$self().connection.send(new ClientboundSetHeldSlotPacket(n));
		}
	}


	@Override
	default void kgy$setMouseItem(ItemStack item) {
		PlayerKGY.super.kgy$setMouseItem(item);

		if (kgy$self().connection != null) {
			kgy$self().inventoryMenu.broadcastChanges();
		}
	}

	default boolean kgy$hasPermission(int i) {
		var permission = new Permission.HasCommandLevel(PermissionLevel.byId(i));
		return kgy$self().permissions().hasPermission(permission);
	}

	@Nullable
	default LevelBlock kgy$getSpawnLocation() {
		var config = kgy$self().getRespawnConfig();
		if (config == null) {
			return null;
		}
		var pos = config.respawnData().pos();
		return pos == null ? null : kgy$getLevel().kgy$getBlock(pos);
	}

	default void kgy$setSpawnLocation(LevelBlock c) {
		var level = c.getLevel();
		var pos = c.getPos();
		var yaw = 0F;
		var pitch = 0F;
		var forced = false;

		var respawn = new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(level.dimension(), pos, yaw, pitch), forced);
		kgy$self().setRespawnPosition(respawn, false);
	}

	@Override
	default void kgy$notify(NotificationToastData builder) {
		KubeGroovyNet.safeSendToPlayer(kgy$self(), new NotificationPayload(builder));
	}

	default void kgy$openChestGUI(Consumer<KubeGroovyGUI> gui) {
		var data = new KubeGroovyGUI();
		gui.accept(data);

		kgy$self().openMenu(new MenuProvider() {
			@Override
			public Component getDisplayName() {
				return data.title;
			}

			@Override
			public AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
				return new KubeGroovyMenu(i, inventory, data);
			}
		}, data::write);
	}

	default void kgy$openInventoryGUI(InventoryKGY inventory, Component title) {
		kgy$openInventoryGUI(inventory, title, inventory.kgy$getWidth());
	}

	default void kgy$openInventoryGUI(InventoryKGY inventory, Component title, int columns) {
		kgy$openInventoryGUI(inventory, title, columns, inventory.kgy$getSlots() / columns);
	}

	default void kgy$openInventoryGUI(InventoryKGY inventory, Component title, int columns, int rows) {
		if (inventory.kgy$getSlots() < columns * rows) {
			throw new RuntimeException("Given container size is unable to contain inventory of size %sx%s!".formatted(columns, rows));
		}
		kgy$openChestGUI(gui -> {
			gui.title = title;
			gui.setInventory(inventory);

			gui.inventoryWidth = columns;
			gui.inventoryHeight = rows;
			gui.height = 114 + gui.inventoryHeight * 18;
			gui.width = 14 + gui.inventoryWidth * 18;
			gui.inventoryLabelY = gui.height - 94;
		});
	}

	@HideFromGY
	default boolean kgy$restoreCapturedInventory() {
		var map = kgy$self().server.kgy$restoreInventories().remove(kgy$self().getUUID());

		if (map == null) {
			return false;
		}

		var playerItems = kgy$self().getInventory().getNonEquipmentItems();

		for (int i = 0; i < playerItems.size(); i++) {
			playerItems.set(i, map.getOrDefault(i, ItemStack.EMPTY));
		}

		return true;
	}

	default Container kgy$captureInventory(boolean autoRestore) {
		var playerItems = kgy$self().getInventory().getNonEquipmentItems();

		var captured = new SimpleContainer(playerItems.size());
		var map = new HashMap<Integer, ItemStack>();

		for (int i = 0; i < playerItems.size(); i++) {
			var c = playerItems.set(i, ItemStack.EMPTY);

			if (!c.isEmpty()) {
				if (autoRestore) {
					map.put(i, c);
				}

				captured.setItem(i, c.copy());
			}
		}

		if (autoRestore && !map.isEmpty()) {
			kgy$self().server.kgy$restoreInventories().put(kgy$self().getUUID(), map);
		}

		return captured;
	}

	default void kgy$openChestGUI(Component title, int rows, Consumer<ChestMenuData> gui) {
		var data = new ChestMenuData(kgy$self(), title, Mth.clamp(rows, 1, 6));
		gui.accept(data);

		if (kgy$self().containerMenu instanceof CustomChestMenu open) {
			data.capturedInventory = open.data.capturedInventory;
		} else {
			data.capturedInventory = kgy$captureInventory(true);
		}

		if (kgy$self().containerMenu instanceof CustomChestMenu open && open.data.rows == data.rows && open.data.title.equals(title)) {
			open.data = data;
			data.sync();
		} else {
			data.sync();

			kgy$self().openMenu(new MenuProvider() {
				@Override
				public Component getDisplayName() {
					return title;
				}

				@Override
				public AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
					return new CustomChestMenu(i, data);
				}
			});
		}
	}

	@Info("Heals the player to full, and fully restores hunger and saturation.")
	default void kgy$heal() {
		kgy$self().heal(kgy$self().getMaxHealth());
		kgy$self().getFoodData().eat(20, 1F);
	}

	@Override
	default void kgy$setActivePostShader(@Nullable Identifier id) {
		KubeGroovyNet.safeSendToPlayer(kgy$self(), new SetActivePostShaderPayload(Optional.ofNullable(id)));
	}
}
