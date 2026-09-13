package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.CommonProperties;
import dev.uncandango.kubegroovy.DevProperties;
import dev.uncandango.kubegroovy.net.KubeGroovyNet;
import dev.uncandango.kubegroovy.net.SendDataFromServerPayload;
import dev.uncandango.kubegroovy.player.EntityArrayList;
import dev.uncandango.kubegroovy.script.ConsoleGY;
import dev.uncandango.kubegroovy.server.ChangesForChat;
import dev.uncandango.kubegroovy.server.DataExport;
import dev.uncandango.kubegroovy.typings.Info;
import dev.uncandango.kubegroovy.typings.Param;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RemapPrefixForGY("kgy$")
public interface MinecraftServerKGY extends WithAttachedData<MinecraftServer>, WithPersistentData, DataSenderKGY, MinecraftEnvironmentKGY, EntityCollectionKGY {
	default MinecraftServer kgy$self() {
		return (MinecraftServer) this;
	}

	ServerLevel kgy$getOverworld();

	@Override
	default Component kgy$getName() {
		return Component.literal(kgy$self().name());
	}

	@Override
	default void kgy$tell(Component message) {
		kgy$self().sendSystemMessage(message);

		for (var player : kgy$self().getPlayerList().getPlayers()) {
			player.kgy$tell(message);
		}
	}

	@Override
	default void kgy$setStatusMessage(Component message) {
		for (var player : kgy$self().getPlayerList().getPlayers()) {
			player.kgy$setStatusMessage(message);
		}
	}

	@Override
	@Info(value = "Runs the specified console command.", params = {
		@Param(name = "command", value = "The console command. Slash at the beginning is optional."),
	})
	default void kgy$runCommand(String command) {
		kgy$self().getCommands().performPrefixedCommand(kgy$self().createCommandSourceStack(), command);
	}

	@Override
	@Info(value = "Runs the specified console command. The command won't output any logs in chat nor console.", params = {
		@Param(name = "command", value = "The console command. Slash at the beginning is optional."),
	})
	default void kgy$runCommandSilent(String command) {
		kgy$self().getCommands().performPrefixedCommand(kgy$self().createCommandSourceStack().withSuppressedOutput(), command);
	}

	@Override
	default void kgy$setActivePostShader(@Nullable Identifier id) {
		for (var player : kgy$self().getPlayerList().getPlayers()) {
			player.kgy$setActivePostShader(id);
		}
	}

	default ServerLevel kgy$getLevel(Identifier dimension) {
		return kgy$self().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
	}

	@Nullable
	default ServerPlayer kgy$getPlayer(PlayerSelector selector) {
		return selector.getPlayer(kgy$self());
	}

	@Override
	default Iterable<? extends Entity> kgy$getMcEntities() {
		var list = new ArrayList<Entity>(10);

		for (var level : kgy$self().getAllLevels()) {
			var i = level.getAllEntities();

			if (i instanceof Collection<Entity> c) {
				list.addAll(c);
			} else {
				for (var e : i) {
					list.add(e);
				}
			}
		}

		return list;
	}

	@Override
	default List<? extends Player> kgy$getMcPlayers() {
		return kgy$self().getPlayerList().getPlayers();
	}

	@Override
	default EntityArrayList<ServerPlayer> kgy$getPlayers() {
		return new EntityArrayList<>(kgy$self().getPlayerList().getPlayers());
	}

	@Override
	@Nullable
	default Entity kgy$getEntityByUUID(UUID id) {
		for (var level : kgy$self().getAllLevels()) {
			var e = level.getEntities().get(id);

			if (e != null) {
				return e;
			}
		}

		return null;
	}

	@Override
	@Nullable
	default Entity kgy$getEntityByNetworkID(int id) {
		for (var level : kgy$self().getAllLevels()) {
			var e = level.getEntities().get(id);

			if (e != null) {
				return e;
			}
		}

		return null;
	}

	@Nullable
	default AdvancementNode kgy$getAdvancement(Identifier id) {
		return kgy$self().getAdvancements().tree().get(id);
	}

	@Override
	default void kgy$sendData(String channel, @Nullable CompoundTag data) {
		KubeGroovyNet.sendToAllPlayers(new SendDataFromServerPayload(channel, data));
	}

	@HideFromGY
	default void kgy$afterResourcesLoaded(boolean reload) {
		if (reload) {
			DataExport.exportData();
		}

		ConsoleGY.SERVER.stopCapturingErrors();

		if (reload && CommonProperties.get().announceReload && !CommonProperties.get().hideServerScriptErrors) {
			if (ConsoleGY.SERVER.errors.isEmpty()) {
				kgy$tell(Component.literal("Reloaded with no KubeGroovy errors!").withStyle(ChatFormatting.GREEN));
			} else {
				kgy$tell(ConsoleGY.SERVER.errorsComponent("/kubegroovy errors server"));
			}

			if (DevProperties.get().logChangesInChat) {
				ChangesForChat.print(this::kgy$tell);
			}
		}

		ConsoleGY.SERVER.info("Server resource reload complete!");
	}

	default Map<UUID, Map<Integer, ItemStack>> kgy$restoreInventories() {
		throw new NoMixinException();
	}
}
