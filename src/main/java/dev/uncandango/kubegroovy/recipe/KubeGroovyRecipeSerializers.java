package dev.uncandango.kubegroovy.recipe;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.recipe.special.ShapedKubeGroovyRecipe;
import dev.uncandango.kubegroovy.recipe.special.ShapelessKubeGroovyRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public interface KubeGroovyRecipeSerializers {
	DeferredRegister<RecipeSerializer<?>> REGISTRY = DeferredRegister.create(Registries.RECIPE_SERIALIZER, KubeGroovy.MOD_ID);

	Supplier<RecipeSerializer<?>> SHAPED = REGISTRY.register("shaped", () -> ShapedKubeGroovyRecipe.SERIALIZER);
	Supplier<RecipeSerializer<?>> SHAPELESS = REGISTRY.register("shapeless", () -> ShapelessKubeGroovyRecipe.SERIALIZER);
}
