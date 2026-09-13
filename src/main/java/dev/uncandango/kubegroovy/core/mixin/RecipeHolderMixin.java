package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.RecipeHolderKGY;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(RecipeHolder.class)
public class RecipeHolderMixin implements RecipeHolderKGY {
	@Unique
	private @Nullable ResourceKey<RecipeSerializer<?>> kgy$serializerKey;

	@Override
	public ResourceKey<RecipeSerializer<?>> kgy$getTypeKey() {
		if (kgy$serializerKey == null) {
			kgy$serializerKey = BuiltInRegistries.RECIPE_SERIALIZER.getResourceKey(kgy$getSerializer()).orElseThrow();
		}

		return kgy$serializerKey;
	}
}
