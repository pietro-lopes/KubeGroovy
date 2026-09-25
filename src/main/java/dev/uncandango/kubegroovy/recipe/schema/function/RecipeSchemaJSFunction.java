package dev.uncandango.kubegroovy.recipe.schema.function;

import dev.uncandango.kubegroovy.recipe.KubeRecipe;
import dev.uncandango.kubegroovy.recipe.RecipeScriptContext;
import dev.uncandango.kubegroovy.script.bridge.BaseFunction;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.type.TypeInfo;

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
	public KubeRecipe call(Object[] args) {
		var cx = Context.getCurrent();
		if (argTypes.length == 0) {
			func.execute(new RecipeScriptContext.Impl(recipe), List.of());
			return recipe;
		}

		var argList = new ArrayList<>(argTypes.length);

		for (int i = 0; i < args.length; i++) {
			argList.add(cx.gyToJava(args[i], argTypes[i]));
		}

		func.execute(new RecipeScriptContext.Impl(recipe), argList);
		return recipe;
	}
}
