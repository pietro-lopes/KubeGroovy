package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.component.DataComponentAccessor;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DataComponentMap.Builder.class)
public abstract class DataComponentMapBuilderMixin implements DataComponentAccessor {
	@Shadow
	public abstract DataComponentMap build();

	@Shadow
	@Final
	private Reference2ObjectMap<DataComponentType<?>, Object> map;

	@Shadow
	@HideFromGY // replaced by kgy$set
	public abstract <T> DataComponentMap.Builder set(DataComponentType<T> component, @Nullable T value);

	@Override
	public <T> void kgy$override(DataComponentType<T> type, @Nullable T value) {
		set(type, value);
	}

	@Override
	public void kgy$remove(DataComponentType<?> type) {
		map.remove(type);
	}
}
