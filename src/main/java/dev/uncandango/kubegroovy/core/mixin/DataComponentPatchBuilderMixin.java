package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.component.DataComponentAccessor;
import dev.uncandango.kubegroovy.util.Cast;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

@Mixin(DataComponentPatch.Builder.class)
public abstract class DataComponentPatchBuilderMixin implements DataComponentAccessor {
	@Shadow
	public abstract DataComponentPatch build();

	@Shadow
	@Final
	private Reference2ObjectMap<DataComponentType<?>, Optional<?>> map;

	@Shadow
	@HideFromGY // replaced by kgy$set, which accepts a nullable value
	public abstract <T> DataComponentPatch.Builder set(DataComponentType<T> component, T value);

	@Override
	public <T> @Nullable T get(DataComponentType<? extends T> type) {
		return map.get(type).map(Cast::<T>to).orElse(null);
	}

	@Override
	public <T> void kgy$override(DataComponentType<T> type, @Nullable T value) {
		if (value == null) {
			kgy$remove(type);
		} else {
			set(type, value);
		}
	}

	@Override
	public void kgy$remove(DataComponentType<?> type) {
		map.remove(type);
	}
}
