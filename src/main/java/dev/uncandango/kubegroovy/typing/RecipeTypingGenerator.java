package dev.uncandango.kubegroovy.typing;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.uncandango.kubegroovy.recipe.RecipeFunctionLookup;
import dev.uncandango.kubegroovy.recipe.RecipeKey;
import dev.uncandango.kubegroovy.recipe.schema.RecipeOptional;
import dev.uncandango.kubegroovy.recipe.schema.RecipeSchemaStorage;
import dev.uncandango.kubegroovy.recipe.schema.RecipeSchemaType;
import dev.uncandango.kubegroovy.script.type.ArrayTypeInfo;
import dev.uncandango.kubegroovy.script.type.GYObjectTypeInfo;
import dev.uncandango.kubegroovy.script.type.JSBasicConstantTypeInfo;
import dev.uncandango.kubegroovy.script.type.JSFixedArrayTypeInfo;
import dev.uncandango.kubegroovy.script.type.JSNumberConstantTypeInfo;
import dev.uncandango.kubegroovy.script.type.JSOrTypeInfo;
import dev.uncandango.kubegroovy.script.type.JSStringConstantTypeInfo;
import dev.uncandango.kubegroovy.script.type.ParameterizedTypeInfo;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Copies schema metadata only: no recipe creation, codecs, or default suppliers are executed. */
public final class RecipeTypingGenerator {
	private RecipeTypingGenerator() {
	}

	public record InputType(String kind, String name, List<InputType> arguments) {
		public InputType {
			arguments = List.copyOf(arguments);
		}
	}

	public record Parameter(String name, InputType type, String description, String defaultValue) {
	}

	public record Method(String name, List<Parameter> parameters, String primaryName) {
		public Method {
			parameters = List.copyOf(parameters);
		}

		public Method(String name, List<Parameter> parameters) {
			this(name, parameters, name);
		}
	}

	public record Recipe(String id, String factoryType, List<Method> constructors, List<Method> methods) {
		public Recipe {
			constructors = List.copyOf(constructors);
			methods = List.copyOf(methods);
		}
	}

	public record Target(String kind, String id) {
	}

	public record Snapshot(int schemaVersion, Map<String, Target> lookups, Map<String, Map<String, String>> namespaces,
		Map<String, String> shortcuts, Map<String, Recipe> recipes) {
		public Snapshot {
			lookups = immutableSorted(lookups);
			var copied = new TreeMap<String, Map<String, String>>();
			namespaces.forEach((name, members) -> copied.put(name, immutableSorted(members)));
			namespaces = immutableSorted(copied);
			shortcuts = immutableSorted(shortcuts);
			recipes = immutableSorted(recipes);
		}

		public static Snapshot empty() {
			return new Snapshot(1, Map.of(), Map.of(), Map.of(), Map.of());
		}
	}

	private static <T> Map<String, T> immutableSorted(Map<String, T> values) {
		return Collections.unmodifiableMap(new TreeMap<>(values));
	}

	public static Snapshot snapshot(RecipeSchemaStorage storage, boolean serverOnly) {
		var recipes = new TreeMap<String, Recipe>();
		var namespaceTargets = new TreeMap<String, Map<String, Target>>();
		var lookups = RecipeFunctionLookup.create(storage, serverOnly, type -> {
			recipes.put(type.id.toString(), describe(type));
			return new Target("recipe", type.id.toString());
		}, (name, members) -> {
			namespaceTargets.put(name, members);
			return new Target("namespace", name);
		});
		// Absent built-in shortcuts may be null in deliberately minimal test storages.
		lookups.values().removeIf(Objects::isNull);
		var namespaces = new TreeMap<String, Map<String, String>>();
		namespaceTargets.forEach((name, targets) -> {
			var members = new TreeMap<String, String>();
			targets.forEach((key, target) -> members.put(key, target.id()));
			namespaces.put(name, members);
		});
		var shortcuts = new TreeMap<>(RecipeFunctionLookup.shortcuts(serverOnly));
		shortcuts.put("vanillaShaped", "minecraft:crafting_shaped");
		shortcuts.put("vanillaShapeless", "minecraft:crafting_shapeless");
		shortcuts.values().removeIf(id -> !recipes.containsKey(id));
		return new Snapshot(1, lookups, namespaces, shortcuts, recipes);
	}

	private static Recipe describe(RecipeSchemaType type) {
		var schema = type.schema;
		var constructors = new ArrayList<Method>();
		for (var entry : new TreeMap<>(schema.constructors()).entrySet()) {
			var constructor = entry.getValue();
			constructors.add(new Method("call", constructor.keys.stream().map(key -> {
				var optional = constructor.overrides.get(key);
				if (optional == null) optional = constructor.defaultValues.get(key);
				return parameter(key, optional == null ? key.optional : optional);
			}).toList()));
		}
		// Runtime JSON construction is a fallback only when no one-argument constructor exists.
		if (!schema.constructors().containsKey(1)) {
			constructors.add(new Method("call", List.of(new Parameter("json", input(TypeInfo.RAW_MAP), "Recipe JSON", ""))));
			constructors.add(new Method("call", List.of(new Parameter("json", input(TypeInfo.of(JsonObject.class)), "Recipe JSON", ""))));
		}
		var methods = new TreeMap<String, Method>();
		// The runtime scans all keys and uses the last matching setter.
		for (var key : schema.keys) {
			for (var name : key.getValidFunctionNames()) {
				methods.put(name, new Method(name, List.of(parameter(key, key.optional)), key.getPrimaryFunctionName()));
			}
		}
		for (var function : schema.functions.values()) {
			if (!RecipeKey.isValidIdentifier(function.name().toCharArray())) continue;
			var parameters = new ArrayList<Parameter>();
			for (int i = 0; i < function.arguments().size(); i++) {
				var component = function.arguments().get(i);
				parameters.add(new Parameter("arg" + (i + 1), input(component.typeInfo()), component.typeInfo().toStringFQN(), ""));
			}
			methods.put(function.name(), new Method(function.name(), parameters));
		}
		return new Recipe(type.id.toString(), schema.recipeFactory.recipeType().toStringFQN().replace('$', '.'),
			constructors, List.copyOf(methods.values()));
	}

	private static Parameter parameter(RecipeKey<?> key, @Nullable RecipeOptional<?> optional) {
		// Only the built-in immutable unit record is read; custom optional implementations are not invoked.
		String defaultValue = optional == RecipeOptional.DEFAULT ? "null" : "";
		if (optional instanceof RecipeOptional.Unit.Impl<?>(var value)) {
			if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) {
				defaultValue = value instanceof Enum<?> constant ? constant.name() : String.valueOf(value);
			}
		}
		return new Parameter(key.name, input(key.typeInfo), key.typeInfo.toStringFQN(), defaultValue);
	}

	public static InputType input(TypeInfo type) {
		if (type instanceof JSOrTypeInfo(var types)) return new InputType("union", "", types.stream().map(RecipeTypingGenerator::input).toList());
		if (type instanceof ArrayTypeInfo) return new InputType("array", "", List.of(input(type.componentType())));
		if (type instanceof ParameterizedTypeInfo generic) {
			var name = generic.rawType().asClass().getCanonicalName();
			return name == null ? input(TypeInfo.OBJECT) : new InputType("class", name,
				Arrays.stream(generic.params()).map(RecipeTypingGenerator::input).toList());
		}
		if (type instanceof GYObjectTypeInfo) return new InputType("class", "java.util.Map", List.of(input(TypeInfo.STRING), input(TypeInfo.OBJECT)));
		if (type instanceof JSFixedArrayTypeInfo) return new InputType("class", "java.util.List", List.of(input(TypeInfo.OBJECT)));
		if (type instanceof JSStringConstantTypeInfo) return input(TypeInfo.STRING);
		if (type instanceof JSNumberConstantTypeInfo) return input(TypeInfo.NUMBER);
		if (type instanceof JSBasicConstantTypeInfo constant) return input(constant.value().equals("true") || constant.value().equals("false") ? TypeInfo.BOOLEAN : TypeInfo.OBJECT);
		var name = type.asClass().getCanonicalName();
		return new InputType("class", name == null || type.asClass() == TypeInfo.class || type == TypeInfo.NONE ? "java.lang.Object" : name, List.of());
	}

	public static String render(Snapshot snapshot) {
		return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(snapshot);
	}
}
