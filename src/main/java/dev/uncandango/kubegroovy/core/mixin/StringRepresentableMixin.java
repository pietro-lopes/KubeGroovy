package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.script.annotations.RemappedEnumConstant;
import net.minecraft.util.StringRepresentable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(StringRepresentable.class)
public interface StringRepresentableMixin extends RemappedEnumConstant {
	@Override
	default String getRemappedEnumConstantName() {
		return ((StringRepresentable) this).getSerializedName();
	}
}
