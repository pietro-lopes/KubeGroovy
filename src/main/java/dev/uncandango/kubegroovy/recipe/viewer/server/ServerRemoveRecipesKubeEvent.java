package dev.uncandango.kubegroovy.recipe.viewer.server;

import dev.uncandango.kubegroovy.recipe.viewer.RemoveRecipesKubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public class ServerRemoveRecipesKubeEvent implements RemoveRecipesKubeEvent {
	private final Set<Identifier> global;
	private final Map<Identifier, CategoryData> categoryData;

	public ServerRemoveRecipesKubeEvent(Set<Identifier> global, Map<Identifier, CategoryData> categoryData) {
		this.global = global;
		this.categoryData = categoryData;
	}

	@Override
	public void remove(Identifier[] recipesToRemove) {
		var cx = Context.getCurrent();
		this.global.addAll(Arrays.asList(recipesToRemove));
	}

	@Override
	public void removeFromCategory(@Nullable Identifier category, Identifier[] recipesToRemove) {
		var cx = Context.getCurrent();
		if (category == null) {
			remove(recipesToRemove);
			return;
		}

		categoryData.computeIfAbsent(category, CategoryData::new).removedRecipes().addAll(Arrays.asList(recipesToRemove));
	}
}
