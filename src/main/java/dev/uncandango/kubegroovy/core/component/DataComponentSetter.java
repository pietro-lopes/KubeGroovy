package dev.uncandango.kubegroovy.core.component;

import dev.uncandango.kubegroovy.util.Cast;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.util.Unit;
import org.jspecify.annotations.Nullable;

@RemapPrefixForGY("kgy$")
public interface DataComponentSetter {
	/// this method exists instead of non-null setters so people can use `set(x, null)` on the GY side
	///
	/// @implNote take care to hide any method named set with this signature, as it is likely not safe for null values!
	<T> void kgy$override(DataComponentType<T> type, @Nullable T value);

	@HideFromGY
	default <T> void kgy$setTyped(TypedDataComponent<T> component) {
		kgy$override(component.type(), component.value());
	}

	default void kgy$remove(DataComponentType<?> type) {
		kgy$override(Cast.to(type), null);
	}

	default void kgy$setUnit(DataComponentType<Unit> component) {
		kgy$override(component, Unit.INSTANCE);
	}

	default void kgy$set(DataComponentMap components) {
		components.forEach(this::kgy$setTyped);
	}

	default void kgy$patch(DataComponentPatch components) {
		for (var entry : components.entrySet()) {
			DataComponentType<?> key = entry.getKey();
			Object value = entry.getValue().orElse(null);

			kgy$override(Cast.to(key), Cast.to(value));
		}
	}
}
