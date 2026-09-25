package dev.uncandango.kubegroovy.integration.emi;

import dev.uncandango.kubegroovy.recipe.viewer.RemoveCategoriesKubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.resources.Identifier;

import java.util.Set;

public class EMIRemoveCategoriesKubeEvent implements RemoveCategoriesKubeEvent {
	private final Set<Identifier> categoriesRemoved;

	public EMIRemoveCategoriesKubeEvent(Set<Identifier> categoriesRemoved) {
		this.categoriesRemoved = categoriesRemoved;
	}

	@Override
	public void remove(Identifier[] categories) {
		var cx = Context.getCurrent();
		categoriesRemoved.addAll(Set.of(categories));
	}
}
