package dev.uncandango.kubegroovy.recipe.schema.function;

import dev.uncandango.kubegroovy.recipe.KubeRecipe;
import dev.uncandango.kubegroovy.recipe.RecipeScriptContext;
import dev.uncandango.kubegroovy.script.bridge.BaseFunction;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.Scriptable;
import dev.uncandango.kubegroovy.script.bridge.TypeInfo;

import java.util.ArrayList;
import java.util.List;

public class RecipeSchemaJSFunction extends BaseFunction {
	public final KubeRecipe recipe;
	public final TypeInfo[] argTypes;
	public final ResolvedRecipeSchemaFunction func;

	public RecipeSchemaJSFunction(KubeRecipe recipe, TypeInfo[] argTypes, ResolvedRecipeSchemaFunction func) {
		this.recipe = recipe;
		this.argTypes = argTypes;
		this.func = func;
	}

	@Override
	public KubeRecipe call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
		if (argTypes.length == 0) {
			func.execute(new RecipeScriptContext.Impl(cx, recipe), List.of());
			return recipe;
		}

		var argList = new ArrayList<>(argTypes.length);

		for (int i = 0; i < args.length; i++) {
			argList.add(cx.gyToJava(args[i], argTypes[i]));
		}

		func.execute(new RecipeScriptContext.Impl(cx, recipe), argList);
		return recipe;
	}
}
