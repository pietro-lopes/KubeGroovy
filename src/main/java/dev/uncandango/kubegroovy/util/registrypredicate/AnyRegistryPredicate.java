package dev.uncandango.kubegroovy.util.registrypredicate;

import net.minecraft.core.Holder;

import java.util.List;

public record AnyRegistryPredicate<T>(List<? extends RegistryPredicate<T>> children) implements RegistryPredicate<T> {
	@Override
	public boolean test(Holder<T> holder) {
		for (RegistryPredicate<T> child : children) {
			if (child.test(holder)) {
				return true;
			}
		}

		return false;
	}
}
