package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.component.DataComponentWrapper;
import dev.uncandango.kubegroovy.core.component.DataComponentAccessor;
import dev.uncandango.kubegroovy.util.RegistryAccessContainer;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.neoforged.neoforge.common.MutableDataComponentHolder;
import org.jspecify.annotations.Nullable;

@RemapPrefixForGY("kgy$")
public interface MutableDataComponentHolderKGY extends DataComponentAccessor {
	@HideFromGY
	default MutableDataComponentHolder kgy$self() {
		throw new NoMixinException();
	}

	default DataComponentMap kgy$getComponentMap() {
		return kgy$self().getComponents();
	}

	@Override
	default <T> void kgy$override(DataComponentType<T> type, @Nullable T value) {
		if (value == null) {
			kgy$self().remove(type);
		} else {
			kgy$self().set(type, value);
		}
	}

	@Override
	default void kgy$set(DataComponentMap components) {
		kgy$self().applyComponents(components);
	}

	@Override
	default void kgy$patch(DataComponentPatch components) {
		kgy$self().applyComponents(components);
	}

	@Override
	default String kgy$getComponentString() {
		var cx = Context.getCurrent();
		if (kgy$getComponentMap() instanceof PatchedDataComponentMap map) {
			return DataComponentWrapper.patchToString(new StringBuilder(), RegistryAccessContainer.of().nbt(), map.asPatch()).toString();
		}

		return "[]";
	}

	default void kgy$resetComponents() {
		var cx = Context.getCurrent();
		if (kgy$getComponentMap() instanceof PatchedDataComponentMap map) {
			map.restorePatch(DataComponentPatch.EMPTY);
		}
	}
}
