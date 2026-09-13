package dev.uncandango.kubegroovy.core.mixin;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import dev.uncandango.kubegroovy.CommonProperties;
import dev.uncandango.kubegroovy.core.RecipeManagerKGY;
import dev.uncandango.kubegroovy.core.ReloadableServerResourcesKGY;
import dev.uncandango.kubegroovy.net.KubeServerData;
import dev.uncandango.kubegroovy.net.SyncServerDataPayload;
import dev.uncandango.kubegroovy.plugin.builtin.event.ServerEvents;
import dev.uncandango.kubegroovy.recipe.RecipesKubeEvent;
import dev.uncandango.kubegroovy.recipe.special.SpecialRecipeSerializerManager;
import dev.uncandango.kubegroovy.script.ConsoleGY;
import dev.uncandango.kubegroovy.script.ScriptType;
import dev.uncandango.kubegroovy.server.ServerScriptManager;
import dev.uncandango.kubegroovy.util.Cast;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

@Mixin(value = RecipeManager.class, priority = 1100)
public abstract class RecipeManagerMixin extends ContextAwareReloadListener implements RecipeManagerKGY {
	@Unique
	private RecipeManager kgy$self() {
		return (RecipeManager) (Object) this;
	}

	@Shadow
	private RecipeMap recipes;

	@Final
	@Shadow
	private HolderLookup.Provider registries;

	@Unique
	private @Nullable ReloadableServerResourcesKGY kgy$resources;

	@WrapOperation(
		method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Lnet/minecraft/world/item/crafting/RecipeMap;",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/packs/resources/SimpleJsonResourceReloadListener;scanDirectoryWithModifier(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/FileToIdConverter;Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/Codec;Ljava/util/Map;Ljava/util/function/Consumer;)V"
		)
	)
	private void injectEventToPost(
		ResourceManager manager,
		FileToIdConverter lister,
		DynamicOps<JsonElement> ops,
		Codec<Recipe<?>> codec,
		Map<Identifier, Recipe<?>> result,
		Consumer<Map<Identifier, JsonElement>> jsonConsumer,
		Operation<Void> original
	) {
		if (kgy$resources == null) {
			return;
		}

		var ssm = Objects.requireNonNull(kgy$resources.kgy$getServerScriptManager());

		// TODO: i would like to be able to live without these two calls
		for (var pending : kgy$resources.kgy$getPostponedTags()) {
			pending.apply();
		}

		for (var pending : kgy$resources.kgy$getNewComponents()) {
			pending.apply();
		}

		for (var entry : ssm.getRegistries().cachedRegistryTags.values()) {
			if (entry.registry() instanceof MappedRegistry<?> mappedRegistry) {
				mappedRegistry.bindTags(Cast.to(entry.lookup().bindingMap()));
			}
		}

		ssm.recipeSchemaStorage.fireEvents(ssm.getRegistries(), manager);

		SpecialRecipeSerializerManager.INSTANCE.reset();
		ServerEvents.SPECIAL_RECIPES.post(ScriptType.SERVER, SpecialRecipeSerializerManager.INSTANCE);

		// this will end up calling the NF event which will post the kube event
		ScopedValue.where(RecipesKubeEvent.INSTANCE, new RecipesKubeEvent(ssm)).run(() -> {
			ConsoleGY.SERVER.info("Processing recipes...");
			original.call(manager, lister, ops, codec, result, jsonConsumer);
		});
	}

	@Inject(
		method = "apply(Lnet/minecraft/world/item/crafting/RecipeMap;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
		at = @At("TAIL")
	)
	private void kgy$applyTail(RecipeMap recipeMap, ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
		if (!CommonProperties.get().serverOnly) {
			kgy$getServerScriptManager().serverData = new SyncServerDataPayload(KubeServerData.collect());
		}
	}

	@Override
	@NullUnmarked
	public ServerScriptManager kgy$getServerScriptManager() {
		return kgy$resources != null ? kgy$resources.kgy$getServerScriptManager() : null;
	}

	@Override
	public void kgy$setResources(ReloadableServerResourcesKGY resources) {
		kgy$resources = resources;
	}

	@Override
	public void kgy$replaceRecipes(RecipeMap recipeMap) {
		recipes = recipeMap;
		ConsoleGY.SERVER.info("Loaded " + recipeMap.values().size() + " recipes");
	}

	@Override
	public Collection<RecipeHolder<?>> kgy$getRecipes() {
		return recipes.values();
	}
}