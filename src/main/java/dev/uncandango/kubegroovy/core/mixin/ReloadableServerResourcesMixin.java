package dev.uncandango.kubegroovy.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.uncandango.kubegroovy.core.ReloadableServerResourcesKGY;
import dev.uncandango.kubegroovy.core.ScriptManagerHolderKGY;
import dev.uncandango.kubegroovy.server.ServerScriptManager;
import dev.uncandango.kubegroovy.util.RegistryAccessContainer;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

@Mixin(ReloadableServerResources.class)
public abstract class ReloadableServerResourcesMixin implements ReloadableServerResourcesKGY {
	@Unique
	@Nullable
	private ServerScriptManager kgy$serverScriptManager;

	@Shadow
	@Final
	private RecipeManager recipes;

	@Shadow
	@Final
	private List<Registry.PendingTags<?>> postponedTags;
	@Shadow
	@Final
	private List<DataComponentInitializers.PendingComponents<?>> newComponents;

	@Override
	public List<DataComponentInitializers.PendingComponents<?>> kgy$getNewComponents() {
		return newComponents;
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	private void kgy$init(
		LayeredRegistryAccess fullLayers, HolderLookup.Provider loadingContext, FeatureFlagSet enabledFeatures, Commands.CommandSelection commandSelection, List postponedTags, PermissionSet functionCompilationPermissions, List newComponents, CallbackInfo ci
	) {
		recipes.kgy$setResources(this);
	}

	@Inject(method = "loadResources", at = @At("HEAD"))
	private static void kgy$injectKubeGroovyPacks(
		ResourceManager resourceManager,
		LayeredRegistryAccess<RegistryLayer> contextLayers,
		List<Registry.PendingTags<?>> updatedContextTags,
		FeatureFlagSet enabledFeatures,
		Commands.CommandSelection commandSelection,
		PermissionSet functionCompilationPermissions,
		Executor backgroundExecutor,
		Executor mainThreadExecutor,
		CallbackInfoReturnable<CompletableFuture<ReloadableServerResources>> cir
	) {
		RegistryAccessContainer.current = new RegistryAccessContainer(contextLayers.compositeAccess());
	}

	@Inject(
		method = "lambda$loadResources$2",
		at = @At(
			value = "INVOKE",
			target = "Lnet/neoforged/neoforge/event/EventHooks;onResourceReload(Lnet/minecraft/server/ReloadableServerResources;Lnet/minecraft/core/RegistryAccess;Ljava/util/Map;)Ljava/util/List;",
			shift = At.Shift.BEFORE
		)
	)
	private static void kgy$bindServerScriptManager(
		ReloadableServerRegistries.LoadResult fullRegistries,
		FeatureFlagSet enabledFeatures,
		Commands.CommandSelection commandSelection,
		List updatedContextTags,
		PermissionSet functionCompilationPermissions,
		ResourceManager resourceManager,
		Executor backgroundExecutor,
		Executor mainThreadExecutor,
		List pendingComponents,
		CallbackInfoReturnable<CompletionStage> cir,
		@Local(name = "result") ReloadableServerResources result
	) {
		var ssm = ((ScriptManagerHolderKGY) resourceManager).kgy$getScriptManager();
		result.kgy$setServerScriptManager(Objects.requireNonNull(ssm, "Missing ServerScriptManager for resource reload!"));
	}

	@Override
	@Nullable
	public ServerScriptManager kgy$getServerScriptManager() {
		return kgy$serverScriptManager;
	}

	@Override
	public void kgy$setServerScriptManager(ServerScriptManager serverScriptManager) {
		kgy$serverScriptManager = serverScriptManager;
	}

	@Override
	public List<Registry.PendingTags<?>> kgy$getPostponedTags() {
		return postponedTags;
	}
}
