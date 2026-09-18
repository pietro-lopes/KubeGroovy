package dev.uncandango.kubegroovy.recipe.component;

import com.mojang.serialization.Codec;
import dev.uncandango.kubegroovy.fluid.FluidWrapper;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.recipe.match.FluidMatch;
import dev.uncandango.kubegroovy.recipe.match.ReplacementMatchInfo;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jspecify.annotations.Nullable;

public record FluidStackComponent(ResourceKey<RecipeComponentType<?>> type, Codec<FluidStack> codec, boolean allowEmpty) implements RecipeComponent<FluidStack> {
	public static final FluidStackComponent FLUID_STACK = new FluidStackComponent(
		RecipeComponentType.builtin("fluid_stack"),
		FluidStack.CODEC, false
	);
	public static final FluidStackComponent OPTIONAL_FLUID_STACK = new FluidStackComponent(
		RecipeComponentType.builtin("optional_fluid_stack"),
		FluidStack.OPTIONAL_CODEC, true
	);

	@Override
	public TypeInfo typeInfo() {
		return FluidWrapper.TYPE_INFO;
	}

	@Override
	public boolean hasPriority(RecipeMatchContext cx, @Nullable Object from) {
		return from instanceof SizedFluidIngredient || from instanceof FluidIngredient || from instanceof FluidStack || from instanceof Fluid;
	}

	@Override
	public boolean matches(RecipeMatchContext cx, FluidStack value, ReplacementMatchInfo match) {
		return match.match() instanceof FluidMatch m && m.matches(cx, value, match.exact());
	}

	@Override
	public boolean isEmpty(FluidStack value) {
		return value.isEmpty();
	}

	@Override
	public void buildUniqueId(UniqueIdBuilder builder, FluidStack value) {
		if (!value.isEmpty()) {
			builder.append(value.getFluid().kgy$getIdLocation());
		}
	}

	@Override
	public String toString() {
		return type.toString();
	}
}
