package dev.uncandango.kubegroovy.recipe;

import dev.uncandango.kubegroovy.plugin.builtin.wrapper.StringUtilsWrapper;
import dev.uncandango.kubegroovy.recipe.schema.RecipeSchemaStorage;
import dev.uncandango.kubegroovy.recipe.schema.RecipeSchemaType;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Shared lookup rules for the runtime recipe map and its IDE snapshot. */
@HideFromGY
@ApiStatus.Internal
public final class RecipeFunctionLookup {
	private RecipeFunctionLookup() {
	}

	public static <T> Map<String, T> create(RecipeSchemaStorage storage, boolean serverOnly,
		Function<RecipeSchemaType, T> recipe, BiFunction<String, Map<String, T>, T> namespace) {
		var functions = new HashMap<String, T>();
		for (var ns : storage.namespaces.values()) {
			var members = new HashMap<String, T>();
			for (var entry : ns.entrySet()) {
				var type = entry.getValue();
				var value = recipe.apply(type);
				members.put(type.id.getPath(), value);
			}
			functions.put(ns.name, namespace.apply(ns.name, members));
			for (var entry : ns.entrySet()) {
				var type = entry.getValue();
				functions.put(type.id.toString(), members.get(type.id.getPath()));
			}
		}
		var shortcutValues = new HashMap<String, T>();
		shortcuts(serverOnly).forEach((name, id) -> shortcutValues.put(name, functions.get(id)));
		for (var entry : new ArrayList<>(functions.entrySet())) {
			if (entry.getKey().indexOf(':') != -1) {
				var alias = StringUtilsWrapper.snakeCaseToCamelCase(entry.getKey());
				if (!alias.equals(entry.getKey())) functions.put(alias, entry.getValue());
			}
		}
		for (var entry : storage.mappings.entrySet()) {
			var value = functions.get(entry.getValue().toString());
			if (value != null) functions.put(entry.getKey(), value);
		}
		functions.putAll(shortcutValues);
		return functions;
	}

	public static Map<String, String> shortcuts(boolean serverOnly) {
		return Map.of("shaped", serverOnly ? "minecraft:crafting_shaped" : "kubegroovy:shaped",
			"shapeless", serverOnly ? "minecraft:crafting_shapeless" : "kubegroovy:shapeless",
			"smelting", "minecraft:smelting", "blasting", "minecraft:blasting", "smoking", "minecraft:smoking",
			"campfireCooking", "minecraft:campfire_cooking", "stonecutting", "minecraft:stonecutting",
			"smithing", "minecraft:smithing_transform", "smithingTrim", "minecraft:smithing_trim");
	}
}
