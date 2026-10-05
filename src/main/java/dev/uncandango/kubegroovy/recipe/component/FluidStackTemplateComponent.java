package dev.uncandango.kubegroovy.recipe.component;

import com.mojang.serialization.Codec;
import dev.uncandango.kubegroovy.fluid.FluidStackTemplateWrapper;
import dev.uncandango.kubegroovy.recipe.RecipeScriptContext;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.recipe.match.FluidMatch;
import dev.uncandango.kubegroovy.recipe.match.ReplacementMatchInfo;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import org.jspecify.annotations.Nullable;

public record FluidStackTemplateComponent(ResourceKey<RecipeComponentType<?>> type, Codec<FluidStackTemplate> codec) implements RecipeComponent<FluidStackTemplate> {
	public static final FluidStackTemplateComponent FLUID_STACK_TEMPLATE = new FluidStackTemplateComponent(
		RecipeComponentType.builtin("fluid_stack_template"), FluidStackTemplate.CODEC
	);

	@Override
	public TypeInfo typeInfo() {
		return FluidStackTemplateWrapper.TYPE_INFO;
	}

	@Override
	public boolean hasPriority(RecipeMatchContext cx, @Nullable Object from) {
		return from instanceof FluidStackTemplate || FluidStackComponent.FLUID_STACK.hasPriority(cx, from);
	}

	@Override
	public boolean matches(RecipeMatchContext cx, FluidStackTemplate value, ReplacementMatchInfo match) {
		return match.match() instanceof FluidMatch m && m.matches(cx, value.create(), match.exact());
	}

	@Override
	public FluidStackTemplate replace(RecipeScriptContext cx, FluidStackTemplate original, ReplacementMatchInfo match, Object with) {
		return matches(cx, original, match) ? wrap(cx, with).withAmount(original.amount()) : original;
	}

	@Override
	public boolean isEmpty(FluidStackTemplate value) {
		return value.amount() <= 0;
	}

	@Override
	public void buildUniqueId(UniqueIdBuilder builder, FluidStackTemplate value) {
		builder.append(value.fluid().value().kgy$getIdLocation());
	}

	@Override
	public String toString() {
		return type.toString();
	}
}
