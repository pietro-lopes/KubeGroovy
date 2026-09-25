package dev.uncandango.kubegroovy.integration.rei;
/*

import dev.architectury.hooks.fluid.forge.FluidStackHooksForge;
import dev.uncandango.kubegroovy.item.ItemPredicate;
import dev.uncandango.kubegroovy.recipe.viewer.RecipeViewerEntryType;
import dev.uncandango.kubegroovy.script.KubeGroovyContext;
import dev.uncandango.kubegroovy.script.bridge.Context;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.EntryType;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

public class REIIntegration {
	// TODO: Re-add RVEType -> EntryType remapping registry

	@Nullable
	public static EntryType<?> typeOf(RecipeViewerEntryType type) {
		if (type == RecipeViewerEntryType.ITEM) {
			return VanillaEntryTypes.ITEM;
		} else if (type == RecipeViewerEntryType.FLUID) {
			return VanillaEntryTypes.FLUID;
		} else {
			return null;
		}
	}

	public static EntryStack<?> stackOf(RecipeViewerEntryType type, Object from) {
		var cx = Context.getCurrent();
		if (type == RecipeViewerEntryType.ITEM) {
			var in = (ItemStack) type.wrapEntry(from);
			return EntryStacks.of(in);
		} else if (type == RecipeViewerEntryType.FLUID) {
			var in = (FluidStack) type.wrapEntry(cx, from);
			return EntryStacks.of(FluidStackHooksForge.fromForge(in));
		} else {
			((KubeGroovyContext) cx).getConsole().error("Currently custom type '" + type.id + "' isn't supported");
			return EntryStack.empty();
		}
	}

	public static EntryIngredient fluidIngredient(FluidIngredient ingredient) {
		FluidStack[] fluidStacks = (FluidStack[]) ingredient.fluids().stream().map(holder -> new FluidStack(holder.value(), 0)).toArray();
		return EntryIngredient.of(Arrays.stream(fluidStacks).map(FluidStackHooksForge::fromForge).map(EntryStacks::of).toList());
	}

	public static EntryIngredient ingredientOf(RecipeViewerEntryType type, Object from) {
		var cx = Context.getCurrent();
		if (type == RecipeViewerEntryType.ITEM) {
			var in = (ItemPredicate) type.wrapPredicate(from);
			return in instanceof Ingredient i ? EntryIngredients.ofIngredient(i) : EntryIngredients.ofItemStacks(Arrays.asList(in.kgy$getStackArray()));
		} else if (type == RecipeViewerEntryType.FLUID) {
			return fluidIngredient((FluidIngredient) type.wrapPredicate(cx, from));
		} else {
			((KubeGroovyContext) cx).getConsole().error("Currently custom type '" + type.id + "' isn't supported");
			return EntryIngredient.empty();
		}
	}
}*/
