package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.MutableDataComponentHolderKGY;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.common.MutableDataComponentHolder;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Supplier;

@RemapPrefixForGY("kgy$")
@Mixin(MutableDataComponentHolder.class)
public interface MutableDataComponentHolderMixin extends MutableDataComponentHolderKGY {
	@HideFromGY
	@Override
	default MutableDataComponentHolder kgy$self() {
		return (MutableDataComponentHolder) this;
	}

	@Shadow
	@HideFromGY
	<T> T set(DataComponentType<T> componentType, T value);

	@Shadow
	@HideFromGY
	<T> T set(Supplier<? extends DataComponentType<T>> componentType, @Nullable T value);

	@Shadow
	@HideFromGY
	<T> T remove(DataComponentType<? extends T> componentType);

	@Shadow
	@HideFromGY
	<T> T remove(Supplier<? extends DataComponentType<? extends T>> componentType);

	@Shadow
	@HideFromGY
	void applyComponents(DataComponentPatch patch);

	@Shadow
	@HideFromGY
	void applyComponents(DataComponentMap components);
}
