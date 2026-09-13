package dev.uncandango.kubegroovy.server;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.command.CommandRegistryKubeEvent;
import dev.uncandango.kubegroovy.command.KubeGroovyCommands;
import dev.uncandango.kubegroovy.gui.chest.CustomChestMenu;
import dev.uncandango.kubegroovy.level.SimpleLevelKubeEvent;
import dev.uncandango.kubegroovy.plugin.builtin.event.LevelEvents;
import dev.uncandango.kubegroovy.plugin.builtin.event.ServerEvents;
import dev.uncandango.kubegroovy.script.ConsoleGY;
import dev.uncandango.kubegroovy.script.PlatformWrapper;
import dev.uncandango.kubegroovy.script.ScriptType;
import dev.uncandango.kubegroovy.util.RegistryAccessContainer;
import dev.uncandango.kubegroovy.web.LocalWebServer;
import dev.uncandango.kubegroovy.web.WebServerProperties;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.visitors.CollectToTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.UUID;

@EventBusSubscriber(modid = KubeGroovy.MOD_ID)
public class KubeGroovyServerEventHandler {
	private static final LevelResource PERSISTENT_DATA = new LevelResource("kubegroovy_persistent_data.nbt");

	@SubscribeEvent
	public static void exportLootTable(LootTableLoadEvent event) {
		if (DataExport.export == null) {
			return;
		}

		Identifier id = event.getName();
		LootTable table = event.getTable();
		HolderLookup.Provider registries = event.getRegistries();

		try {
			var ops = registries.createSerializationContext(JsonOps.INSTANCE);
			var result = LootTable.DIRECT_CODEC.encodeStart(ops, table);

			result.ifSuccess(element -> {
				if (element instanceof JsonObject json) {
					var fileName = "%s/%s/%s/%s.json".formatted(
						Registries.LOOT_TABLE.identifier().getNamespace(),
						Registries.LOOT_TABLE.identifier().getPath(),
						id.getNamespace(),
						id.getPath()
					);
					DataExport.export.addJson(fileName, json);
				}
			});
		} catch (Exception ex) {
			ConsoleGY.SERVER.error("Failed to export loot table %s as JSON!".formatted(id), ex);
		}
	}

	@SubscribeEvent
	public static void registerCommands(RegisterCommandsEvent event) {
		KubeGroovyCommands.register(event.getDispatcher());

		if (ServerEvents.COMMAND_REGISTRY.hasListeners()) {
			ServerEvents.COMMAND_REGISTRY.post(ScriptType.SERVER, new CommandRegistryKubeEvent(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection()));
		}
	}

	@SubscribeEvent
	public static void serverBeforeStart(ServerAboutToStartEvent event) {
		var server = event.getServer();

		if (FMLEnvironment.getDist() == Dist.DEDICATED_SERVER && !PlatformWrapper.isGeneratingData() && WebServerProperties.get().enabled && !WebServerProperties.get().publicAddress.isEmpty()) {
			LocalWebServer.start(server, false);
		}

		var p = server.getWorldPath(PERSISTENT_DATA);

		if (Files.exists(p)) {
			try {
				var output = new CollectToTag();
				NbtIo.parseCompressed(p, output, NbtAccounter.unlimitedHeap());
				var parsed = output.getResult();

				if (parsed instanceof CompoundTag tag) {
					var access = server.registryAccess();
					var ops = access.createSerializationContext(NbtOps.INSTANCE);
					var tOpt = tag.getCompound("__restore_inventories");

					if (tOpt.isPresent()) {
						tag.remove("__restore_inventories");

						var t = tOpt.get();
						var playerMap = server.kgy$restoreInventories();

						for (var key : t.keySet()) {
							var list = t.getList(key).orElseGet(ListTag::new);

							if (!list.isEmpty() && list.getFirst().getId() != (byte) 10) {
								continue;
							}

							var map = playerMap.computeIfAbsent(UUID.fromString(key), k -> new HashMap<>());

							for (var tag2 : list) {
								if (!(tag2 instanceof CompoundTag c)) {
									continue;
								}

								var slot = c.getShort("Slot").orElse((short) 0);
								var stackResult = ItemStack.CODEC.parse(ops, tag2);
								stackResult.result().ifPresent(itemStack -> map.put((int) slot, itemStack));

							}
						}
					}
					server.kgy$getPersistentData().merge(tag);
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	@SubscribeEvent
	public static void serverStarting(ServerStartingEvent event) {
		ServerEvents.LOADED.post(ScriptType.SERVER, new ServerKubeEvent(event.getServer()));
	}

	@SubscribeEvent
	public static void serverStopping(ServerStoppingEvent event) {
		ServerEvents.UNLOADED.post(ScriptType.SERVER, new ServerKubeEvent(event.getServer()));
	}

	@SubscribeEvent
	public static void serverStopped(ServerStoppedEvent event) {
		RegistryAccessContainer.current = RegistryAccessContainer.BUILTIN;
		ScheduledServerEvent.EVENTS.clear();
	}

	@SubscribeEvent
	public static void serverLevelLoaded(LevelEvent.Load event) {
		if (event.getLevel() instanceof ServerLevel level && LevelEvents.LOADED.hasListeners(level.dimension())) {
			LevelEvents.LOADED.post(new SimpleLevelKubeEvent(level), level.dimension());
		}
	}

	@SubscribeEvent
	public static void serverLevelSaved(LevelEvent.Save event) {
		if (event.getLevel() instanceof ServerLevel level && LevelEvents.SAVED.hasListeners(level.dimension())) {
			LevelEvents.SAVED.post(new SimpleLevelKubeEvent(level), level.dimension());
		}

		if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
			var serverData = level.getServer().kgy$getPersistentData().copy();
			var p = level.getServer().getWorldPath(PERSISTENT_DATA);

			var playerMap = level.getServer().kgy$restoreInventories();

			if (!playerMap.isEmpty()) {
				var nbt = new CompoundTag();

				for (var entry : playerMap.entrySet()) {
					var list = new ListTag();

					for (var entry2 : entry.getValue().entrySet()) {
						var tag = new CompoundTag();
						tag.putShort("Slot", entry2.getKey().shortValue());
						RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
						Tag itemTag = ItemStack.CODEC.encodeStart(ops, entry2.getValue()).getOrThrow();
						tag.put("Item", itemTag);
						list.add(tag);
					}

					nbt.put(entry.getKey().toString(), list);
				}

				serverData.put("__restore_inventories", nbt);
			}

			Util.ioPool().execute(() -> {
				try {
					NbtIo.writeCompressed(serverData, p);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			});
		}
	}

	@SubscribeEvent
	public static void command(CommandEvent event) {
		if (ServerEvents.COMMAND.hasListeners()) {
			var e = new CommandKubeEvent(event);

			if (ServerEvents.COMMAND.hasListeners(e.getCommandName())) {
				ServerEvents.COMMAND.post(e, e.getCommandName()).applyCancel(event);
			}
		}
	}

	@SubscribeEvent
	public static void addReloadListeners(AddServerReloadListenersEvent event) {
		event.addListener(Identifier.fromNamespaceAndPath(KubeGroovy.MOD_ID, "kubegroovy_resources"), new KubeGroovyReloadListener(event.getServerResources()));
	}

	@SubscribeEvent
	public static void preventPickupDuringChestGUI(ItemEntityPickupEvent.Pre event) {
		var e = event.getPlayer();

		if (e instanceof ServerPlayer player && player.isAlive() && !player.hasDisconnected() && player.containerMenu instanceof CustomChestMenu) {
			event.setCanPickup(TriState.FALSE);
		}
	}
}