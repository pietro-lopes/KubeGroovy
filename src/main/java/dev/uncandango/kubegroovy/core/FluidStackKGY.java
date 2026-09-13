package dev.uncandango.kubegroovy.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import dev.uncandango.kubegroovy.component.DataComponentWrapper;
import dev.uncandango.kubegroovy.fluid.FluidLike;
import dev.uncandango.kubegroovy.fluid.FluidWrapper;
import dev.uncandango.kubegroovy.recipe.RecipeScriptContext;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.recipe.match.FluidMatch;
import dev.uncandango.kubegroovy.recipe.match.Replaceable;
import dev.uncandango.kubegroovy.util.Cast;
import dev.uncandango.kubegroovy.util.ID;
import dev.uncandango.kubegroovy.util.WithCodec;
import dev.uncandango.kubegroovy.web.RelativeURL;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.annotations.SpecialEquality;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public interface FluidStackKGY extends
	Replaceable,
	SpecialEquality,
	WithCodec,
	FluidLike,
	FluidMatch,
	MutableDataComponentHolderKGY,
	RegistryObjectKGY<Fluid> {
	default FluidStack kgy$self() {
		return Cast.to(this);
	}

	@Override
	default boolean specialEquals(Context cx, @Nullable Object o, boolean shallow) {
		return switch (o) {
			case CharSequence cs -> kgy$getId().equals(ID.string(cs.toString()));
			case Identifier id -> kgy$getIdLocation().equals(id);
			case FluidStack s -> kgy$equalsIgnoringCount(s);
			case null, default -> kgy$equalsIgnoringCount(FluidWrapper.wrap(cx, o));
		};
	}

	default boolean kgy$equalsIgnoringCount(FluidStack stack) {
		var self = kgy$self();

		if (self == stack) {
			return true;
		} else if (self.isEmpty()) {
			return stack.isEmpty();
		}

		return FluidStack.isSameFluidSameComponents(self, stack);
	}

	@Override
	default ResourceKey<Registry<Fluid>> kgy$getRegistryId() {
		return Registries.FLUID;
	}

	@Override
	default Registry<Fluid> kgy$getRegistry() {
		return BuiltInRegistries.FLUID;
	}

	@Override
	default Identifier kgy$getIdLocation() {
		return kgy$self().getFluid().kgy$getIdLocation();
	}

	@Override
	default Holder<Fluid> kgy$asHolder() {
		return kgy$self().getFluid().kgy$asHolder();
	}

	@Override
	default ResourceKey<Fluid> kgy$getKey() {
		return kgy$self().getFluid().kgy$getKey();
	}

	@Override
	default String kgy$getId() {
		return kgy$self().getFluid().kgy$getId();
	}

	@Override
	default String kgy$getMod() {
		return kgy$self().getFluid().kgy$getMod();
	}

	@Override
	default int kgy$getAmount() {
		return kgy$self().getAmount();
	}

	@Override
	default boolean kgy$isEmpty() {
		return kgy$self().isEmpty();
	}

	@Override
	default Fluid kgy$getFluid() {
		return kgy$self().getFluid();
	}

	@Override
	default FluidLike kgy$copy(int amount) {
		return (FluidLike) (Object) kgy$self().copyWithAmount(amount);
	}

	@Override
	default Codec<?> getCodec(Context cx) {
		return FluidStack.CODEC;
	}

	@Override
	default Object replaceThisWith(RecipeScriptContext cx, Object with) {
		var t = kgy$self();
		var r = FluidWrapper.wrap(cx.cx(), with);

		if (!FluidStack.isSameFluidSameComponents(t, r)) {
			r.setAmount(t.getAmount());
			return r;
		}

		return this;
	}

	@Override
	default boolean matches(RecipeMatchContext cx, FluidStack s, boolean exact) {
		return kgy$self().getFluid() == s.getFluid();
	}

	@Override
	default boolean matches(RecipeMatchContext cx, FluidIngredient ingredient, boolean exact) {
		return ingredient.test(kgy$self());
	}

	default RelativeURL kgy$getWebIconURL(DynamicOps<Tag> ops, int size) {
		var url = "/img/" + size + "/fluid/" + ID.url(kgy$getIdLocation());
		var c = DataComponentWrapper.patchToString(new StringBuilder(), ops, DataComponentWrapper.visualPatch(kgy$self().getComponentsPatch())).toString();
		return new RelativeURL(url, c.equals("[]") ? Map.of() : Map.of("components", c.substring(1, c.length() - 1)));
	}
}
