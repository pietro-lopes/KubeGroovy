package dev.uncandango.kubegroovy.recipe;

import dev.uncandango.kubegroovy.recipe.component.RecipeComponent;
import dev.uncandango.kubegroovy.recipe.component.RecipeComponentValueFunction;
import dev.uncandango.kubegroovy.recipe.schema.function.RecipeSchemaJSFunction;
import dev.uncandango.kubegroovy.script.bridge.BaseFunction;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.NativeJavaObject;
import dev.uncandango.kubegroovy.script.bridge.Scriptable;
import dev.uncandango.kubegroovy.script.bridge.TypeInfo;

import java.util.HashMap;
import java.util.Map;

public class RecipeFunction extends NativeJavaObject {
	public final KubeRecipe recipe;
	public final Map<String, BaseFunction> builderFunctions;

	public static boolean isValidIdentifier(char[] name) {
		if (name.length == 0 || !Character.isJavaIdentifierStart(name[0])) {
			return false;
		}

		for (int i = 1; i < name.length; i++) {
			if (!Character.isJavaIdentifierPart(name[i])) {
				return false;
			}
		}

		return true;
	}

	public RecipeFunction(Context cx, Scriptable scope, TypeInfo staticType, KubeRecipe recipe) {
		super(scope, recipe, staticType, cx);
		this.recipe = recipe;
		this.builderFunctions = new HashMap<>();

		for (var value : recipe.getRecipeComponentValues()) {
			var names = value.key.getValidFunctionNames();

			if (!names.isEmpty()) {
				var func = new RecipeComponentValueFunction(recipe, value);

				for (var name : names) {
					builderFunctions.put(name, func);
				}
			}
		}

		for (var func : recipe.type.schemaType.schema.functions.values()) {
			if (isValidIdentifier(func.name().toCharArray())) {
				builderFunctions.put(func.name(), new RecipeSchemaJSFunction(recipe, func.arguments().stream().map(RecipeComponent::typeInfo).toArray(TypeInfo[]::new), func.function()));
			}
		}
	}

	@Override
	public Object get(Context cx, String name, Scriptable start) {
		var s = super.get(cx, name, start);

		if (s == Scriptable.NOT_FOUND) {
			var r = builderFunctions.get(name);

			if (r != null) {
				return r;
			}
		}

		return s;
	}
}
