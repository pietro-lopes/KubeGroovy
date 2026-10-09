package dev.uncandango.kubegroovy.fluid;

import dev.uncandango.kubegroovy.script.type.TypeInfo;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import org.jspecify.annotations.Nullable;

public interface FluidStackTemplateWrapper {
	TypeInfo TYPE_INFO = TypeInfo.of(FluidStackTemplate.class);

	static FluidStackTemplate wrap(@Nullable Object from) {
		return from instanceof FluidStackTemplate template ? template : FluidStackTemplate.fromNonEmptyStack(FluidWrapper.wrap(from));
	}
}
