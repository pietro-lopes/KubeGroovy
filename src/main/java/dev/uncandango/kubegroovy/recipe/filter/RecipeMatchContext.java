package dev.uncandango.kubegroovy.recipe.filter;

import dev.uncandango.kubegroovy.core.RecipeLikeKGY;
import dev.uncandango.kubegroovy.recipe.KubeRecipe;
import dev.uncandango.kubegroovy.recipe.RecipeLikeContext;
import dev.uncandango.kubegroovy.util.RegistryAccessContainer;
import dev.uncandango.kubegroovy.util.RegistryOpsContainer;
import dev.uncandango.kubegroovy.script.bridge.Context;

public interface RecipeMatchContext extends RecipeLikeContext {
	final class Impl implements RecipeMatchContext {
		private final Context cx;
		private final RegistryAccessContainer registries;
		private final RegistryOpsContainer ops;
		private final RecipeLikeKGY recipe;

		Impl(Context cx, RegistryAccessContainer registries, RegistryOpsContainer ops, RecipeLikeKGY recipe) {
			this.cx = cx;
			this.registries = registries;
			this.ops = ops;
			this.recipe = recipe;
		}

		Impl(Context cx, KubeRecipe recipe) {
			this(cx, recipe.type.event.registries, recipe.type.event.ops, recipe);
		}

		public Impl(RegistryAccessContainer registries, RegistryOpsContainer ops, RecipeLikeKGY recipe) {
			var cx = Context.getCurrent();
			this.cx = cx;
			this.registries = registries;
			this.ops = ops;
			this.recipe = recipe;
		}

		public Impl(KubeRecipe recipe) {
			var cx = Context.getCurrent();
			this.cx = cx;
			this.registries = recipe.type.event.registries;
			this.ops = recipe.type.event.ops;
			this.recipe = recipe;
		}

		public Context cx() {
			return cx;
		}

		@Override
		public RegistryAccessContainer registries() {
			return registries;
		}

		@Override
		public RegistryOpsContainer ops() {
			return ops;
		}

		@Override
		public RecipeLikeKGY recipe() {
			return recipe;
		}
	}
}
