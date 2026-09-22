package dev.uncandango.kubegroovy.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.uncandango.kubegroovy.core.ScriptManagerHolderKGY;
import dev.uncandango.kubegroovy.core.TagLoaderKGY;
import dev.uncandango.kubegroovy.plugin.builtin.event.ServerEvents;
import dev.uncandango.kubegroovy.registry.RegistryObjectStorage;
import dev.uncandango.kubegroovy.server.ServerScriptManager;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.tags.TagLoader.EntryWithSource;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(TagLoader.class)
public abstract class TagLoaderMixin<T> implements TagLoaderKGY<T> {
	@Unique
	private @Nullable ServerScriptManager kgy$serverScriptManager;

	@Unique
	private @Nullable Registry<T> kgy$storedRegistry;

	@Override
	public void kgy$init(@Nullable ServerScriptManager serverScriptManager, Registry<T> registry) {
		kgy$serverScriptManager = serverScriptManager;
		kgy$storedRegistry = registry;
	}

	@Inject(
		method = "loadPendingTags(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/core/Registry;)Ljava/util/Optional;",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/tags/TagLoader;load(Lnet/minecraft/server/packs/resources/ResourceManager;)Ljava/util/Map;",
			shift = At.Shift.BEFORE
		)
	)
	private static <T> void kgy$initLoaderBeforeLoad(
		ResourceManager manager,
		Registry<T> registry,
		CallbackInfoReturnable<?> cir,
		@Local(name = "loader") TagLoader<T> loader
	) {
		loader.kgy$init(((ScriptManagerHolderKGY) manager).kgy$getScriptManager(), registry);
	}

	@Inject(method = "load", at = @At("RETURN"))
	private void kgy$modifyLoadedTags(
		ResourceManager manager,
		CallbackInfoReturnable<Map<Identifier, List<EntryWithSource>>> cir
	) {
		var ssm = kgy$getServerScriptManager();

		if (ssm != null && kgy$storedRegistry != null) {
			Map<Identifier, List<EntryWithSource>> map = cir.getReturnValue();
			var reg = kgy$getRegistry();

			if (reg == null) {
				return;
			}

			boolean needsCustomTags = false;
			var objStorage = RegistryObjectStorage.of(reg.key());

			for (var builder : objStorage.objects.values()) {
				if (!builder.kgy$getDefaultTags().isEmpty()) {
					needsCustomTags = true;
					break;
				}
			}

			needsCustomTags |= !ssm.serverRegistryTags.isEmpty() || ServerEvents.TAGS.hasListeners(objStorage.key);

			if (needsCustomTags) {
				kgy$customTags(map);
			}

			ssm.getRegistries().cacheTags(reg, map);
		}
	}

	@Override
	@Nullable
	public ServerScriptManager kgy$getServerScriptManager() {
		return kgy$serverScriptManager;
	}

	@Override
	public @Nullable Registry<T> kgy$getRegistry() {
		return kgy$storedRegistry;
	}
}
