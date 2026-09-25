package dev.uncandango.kubegroovy.recipe.viewer.server;

import dev.uncandango.kubegroovy.recipe.viewer.RemoveCategoriesKubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.Set;

public class ServerRemoveCategoriesKubeEvent implements RemoveCategoriesKubeEvent {
	private final Set<Identifier> categories;

	public ServerRemoveCategoriesKubeEvent(Set<Identifier> categories) {
		this.categories = categories;
	}

	@Override
	public void remove(Identifier[] categories) {
		var cx = Context.getCurrent();
		this.categories.addAll(Arrays.asList(categories));
	}
}
