package dev.uncandango.kubegroovy.recipe.component;

import dev.uncandango.kubegroovy.recipe.KubeRecipe;
import dev.uncandango.kubegroovy.recipe.RecipeScriptContext;
import dev.uncandango.kubegroovy.util.Cast;
import dev.uncandango.kubegroovy.script.bridge.BaseFunction;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.Scriptable;
import dev.uncandango.kubegroovy.script.bridge.Wrapper;

public class RecipeComponentValueFunction extends BaseFunction {
	public final KubeRecipe recipe;
	public final RecipeComponentValue<?> componentValue;

	public RecipeComponentValueFunction(KubeRecipe recipe, RecipeComponentValue<?> componentValue) {
		this.recipe = recipe;
		this.componentValue = componentValue;
	}

	@Override
	public KubeRecipe call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
		return recipe.setValue(componentValue.key, Cast.to(componentValue.key.component.wrap(new RecipeScriptContext.Impl(cx, recipe), Wrapper.unwrapped(args[0]))));
	}
}
