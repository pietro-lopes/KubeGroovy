package dev.uncandango.kubegroovy.recipe.viewer;

import dev.uncandango.kubegroovy.event.KubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public interface RemoveRecipesKubeEvent extends KubeEvent {
	default void remove(Identifier[] recipesToRemove) {
		var cx = Context.getCurrent();
		removeFromCategory(null, recipesToRemove);
	}

	void removeFromCategory(@Nullable Identifier category, Identifier[] recipesToRemove);
}
