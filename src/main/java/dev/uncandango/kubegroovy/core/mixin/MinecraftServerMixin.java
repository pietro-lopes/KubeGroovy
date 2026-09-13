package dev.uncandango.kubegroovy.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.uncandango.kubegroovy.core.MinecraftServerKGY;
import dev.uncandango.kubegroovy.gui.chest.CustomChestMenu;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugin;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugins;
import dev.uncandango.kubegroovy.plugin.builtin.event.ServerEvents;
import dev.uncandango.kubegroovy.script.ScriptType;
import dev.uncandango.kubegroovy.server.ScheduledServerEvent;
import dev.uncandango.kubegroovy.server.ServerKubeEvent;
import dev.uncandango.kubegroovy.server.ServerScriptManager;
import dev.uncandango.kubegroovy.util.AttachedData;
import dev.uncandango.kubegroovy.util.ScheduledEvents;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
@RemapPrefixForGY("kgy$")
public abstract class MinecraftServerMixin implements MinecraftServerKGY {
	@Shadow
	protected abstract boolean initServer() throws IOException;

	@Shadow
	public abstract void invalidateStatus();

	@Unique
	private final CompoundTag kgy$persistentData = new CompoundTag();

	@Unique
	private @Nullable ServerLevel kgy$overworld;

	@Unique
	private @Nullable AttachedData<MinecraftServer> kgy$attachedData;

	@Unique
	private final Map<UUID, Map<Integer, ItemStack>> kgy$restoreInventories = new HashMap<>(1);

	@Inject(method = "<init>", at = @At("RETURN"))
	private void kgy$init(CallbackInfo ci) {
		CompletableFuture.runAsync(() -> kgy$afterResourcesLoaded(false), kgy$self());
	}

	@Shadow
	public abstract RegistryAccess.Frozen registryAccess();

	@Override
	public CompoundTag kgy$getPersistentData() {
		return kgy$persistentData;
	}

	@Override
	public AttachedData<MinecraftServer> kgy$getData() {
		if (kgy$attachedData == null) {
			kgy$attachedData = new AttachedData<>(kgy$self());
			KubeGroovyPlugins.forEachPlugin(kgy$attachedData, KubeGroovyPlugin::attachServerData);
		}

		return kgy$attachedData;
	}

	@Override
	public ServerLevel kgy$getOverworld() {
		if (kgy$overworld == null) {
			kgy$overworld = kgy$self().overworld();
		}

		return kgy$overworld;
	}

	@Inject(method = "tickServer", at = @At("RETURN"))
	private void kgy$postTickServer(BooleanSupplier booleanSupplier, CallbackInfo ci) {
		ScheduledServerEvent.EVENTS.tickAll(kgy$getOverworld().getGameTime());

		if (!kgy$restoreInventories.isEmpty()) {
			for (var player : kgy$self().getPlayerList().getPlayers()) {
				if (kgy$restoreInventories.containsKey(player.getUUID()) && player.isAlive() && !player.hasDisconnected() && !(player.containerMenu instanceof CustomChestMenu)) {
					player.kgy$restoreCapturedInventory();
				}
			}
		}

		if (ServerEvents.TICK.hasListeners()) {
			ServerEvents.TICK.post(ScriptType.SERVER, new ServerKubeEvent(kgy$self()));
		}
	}

	@Override
	public ScheduledEvents kgy$getScheduledEvents() {
		return ScheduledServerEvent.EVENTS;
	}

	@Override
	public Map<UUID, Map<Integer, ItemStack>> kgy$restoreInventories() {
		return kgy$restoreInventories;
	}

	@Shadow
	@RemapForGY("isDedicated")
	public abstract boolean isDedicatedServer();

	@Shadow
	@RemapForGY("stop")
	public abstract void stopServer();

	@Shadow
	public abstract MinecraftServer.ReloadableResources getServerResources();

	@Inject(method = "reloadResources", at = @At("TAIL"))
	private void kgy$endResourceReload(Collection<String> collection, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
		CompletableFuture.runAsync(() -> kgy$afterResourcesLoaded(true), kgy$self());
	}

	// There's a good chance this will break in the future, but currently that's the only reliable way to both inject packs and reload scripts before resources that I could find
	@WrapOperation(method = "lambda$reloadResources$1", at = @At(
		value = "NEW",
		target = "(Lnet/minecraft/server/packs/PackType;Ljava/util/List;)Lnet/minecraft/server/packs/resources/MultiPackResourceManager;")
	)
	private MultiPackResourceManager kgy$createResourceManager(PackType type, List<PackResources> original, Operation<MultiPackResourceManager> ctor) {
		return ServerScriptManager.bindServerResources(original, false, ctor::call);
	}
}
