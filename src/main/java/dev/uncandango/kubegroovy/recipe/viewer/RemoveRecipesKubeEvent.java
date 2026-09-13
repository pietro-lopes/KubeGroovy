package dev.uncandango.kubegroovy.recipe.viewer;

import dev.uncandango.kubegroovy.event.KubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public interface RemoveRecipesKubeEvent extends KubeEvent {
	default void remove(Context cx, Identifier[] recipesToRemove) {
		removeFromCategory(cx, null, recipesToRemove);
	}

	void removeFromCategory(Context cx, @Nullable Identifier category, Identifier[] recipesToRemove);
}
