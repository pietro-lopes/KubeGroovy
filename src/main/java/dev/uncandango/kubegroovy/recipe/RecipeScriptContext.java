package dev.uncandango.kubegroovy.recipe;

import dev.uncandango.kubegroovy.recipe.component.RecipeValidationContext;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.script.WithScriptContext;
import dev.uncandango.kubegroovy.util.ErrorStack;
import dev.uncandango.kubegroovy.script.bridge.Context;

public interface RecipeScriptContext extends WithScriptContext, RecipeValidationContext, KubeRecipeContext, RecipeMatchContext {
	final class Impl implements RecipeScriptContext {
		private final Context cx;
		private final KubeRecipe recipe;
		private final ErrorStack errors;

		Impl(Context cx, KubeRecipe recipe, ErrorStack errors) {
			this.cx = cx;
			this.recipe = recipe;
			this.errors = errors;
		}

		Impl(Context cx, KubeRecipe recipe) {
			this(cx, recipe, ErrorStack.NONE);
		}

		public Impl(KubeRecipe recipe) {
			this(recipe, ErrorStack.NONE);
		}

		public Impl(KubeRecipe recipe, ErrorStack errors) {
			var cx = Context.getCurrent();
			this.cx = cx;
			this.recipe = recipe;
			this.errors = errors;
		}

		@Override
		public Context cx() {
			return cx;
		}

		@Override
		public KubeRecipe recipe() {
			return recipe;
		}

		@Override
		public ErrorStack errors() {
			return errors;
		}
	}
}
