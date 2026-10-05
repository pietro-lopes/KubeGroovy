package dev.uncandango.kubegroovy.typingtest;

import dev.uncandango.kubegroovy.typing.RecipeTypingGenerator;

import dev.uncandango.kubegroovy.recipe.RecipeFunctionLookup;
import dev.uncandango.kubegroovy.recipe.component.NumberComponent;
import dev.uncandango.kubegroovy.recipe.component.StringComponent;
import dev.uncandango.kubegroovy.recipe.schema.KubeRecipeFactory;
import dev.uncandango.kubegroovy.recipe.schema.RecipeConstructor;
import dev.uncandango.kubegroovy.recipe.schema.RecipeSchema;
import dev.uncandango.kubegroovy.recipe.schema.RecipeSchemaStorage;
import dev.uncandango.kubegroovy.recipe.schema.UnknownRecipeSchema;
import dev.uncandango.kubegroovy.recipe.schema.function.RecipeFunctionInstance;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeTypingGeneratorTests {
	private RecipeSchemaStorage storage() {
		// Snapshotting needs only registrations, never a server manager or registry access.
		return new RecipeSchemaStorage(null);
	}

	@Test
	void lookupUsesRuntimeAliasesMappingsAndShortcutPrecedence() {
		var storage = storage();
		var schema = new RecipeSchema(StringComponent.STRING.outputKey("result"));
		storage.namespace("minecraft").register("crafting_shaped", schema).register("smelting", schema).register("campfire_cooking", schema);
		storage.namespace("kubegroovy").register("shaped", schema);
		storage.mappings.put("shaped", Identifier.parse("minecraft:smelting"));
		storage.mappings.put("alias", Identifier.parse("minecraft:smelting"));
		storage.mappings.put("missing", Identifier.parse("missing:recipe"));
		storage.mappings.put("minecraft:crafting_shaped", Identifier.parse("minecraft:smelting"));
		var snapshot = RecipeTypingGenerator.snapshot(storage, false);
		assertEquals("kubegroovy:shaped", snapshot.lookups().get("shaped").id());
		assertEquals("minecraft:crafting_shaped", RecipeTypingGenerator.snapshot(storage, true).lookups().get("shaped").id());
		assertEquals("minecraft:smelting", snapshot.lookups().get("alias").id());
		assertEquals("minecraft:smelting", snapshot.lookups().get("minecraft:crafting_shaped").id());
		assertEquals("minecraft:crafting_shaped", snapshot.shortcuts().get("vanillaShaped"));
		assertEquals("minecraft:campfire_cooking", snapshot.lookups().get("minecraftCampfireCooking").id());
		assertEquals("minecraft:smelting", snapshot.namespaces().get("minecraft").get("smelting"));
		assertFalse(snapshot.lookups().containsKey("missing"));
		var lookup = RecipeFunctionLookup.<Object>create(storage, false, type -> type.id.toString(), (name, members) -> members);
		assertEquals(snapshot.lookups().get("alias").id(), lookup.get("alias"));
		assertEquals("minecraft:smelting", ((Map<?, ?>) lookup.get("minecraft")).get("smelting"));
	}

	@Test
	void snapshotsActualConstructorsSettersAndFunctionsWithoutExecutingUserCode() {
		var storage = storage();
		var calls = new AtomicInteger();
		var result = StringComponent.STRING.outputKey("result");
		var input = StringComponent.STRING.inputKey("ingredient");
		var time = NumberComponent.INT.otherKey("cooking_time").optional(200).functionNames("cookingTime", "duration", "time");
		var hidden = StringComponent.STRING.otherKey("hidden").optional("hidden").exclude().noFunctions();
		var dynamic = StringComponent.STRING.otherKey("dynamic").optional(type -> {
			calls.incrementAndGet();
			throw new AssertionError("Default supplier was evaluated");
		});
		var schema = new RecipeSchema(result, input, time, hidden, dynamic);
		schema.factory(new KubeRecipeFactory(Identifier.parse("test:factory"), TypeInfo.of(dev.uncandango.kubegroovy.recipe.KubeRecipe.class), () -> {
			calls.incrementAndGet();
			throw new AssertionError("Factory was evaluated");
		}));
		schema.constructor(new RecipeConstructor(input, result));
		schema.constructor(new RecipeConstructor(input, result, time));
		schema.function(new RecipeFunctionInstance("time", List.of(StringComponent.STRING), (cx, args) -> calls.incrementAndGet()));
		storage.namespace("test").register("recipe", schema);
		var snapshot = RecipeTypingGenerator.snapshot(storage, false);
		var recipe = snapshot.recipes().get("test:recipe");
		assertEquals(List.of("ingredient", "result"), recipe.constructors().getFirst().parameters().stream().map(RecipeTypingGenerator.Parameter::name).toList());
		assertEquals(List.of(2, 3, 1, 1), recipe.constructors().stream().map(method -> method.parameters().size()).toList());
		assertFalse(recipe.methods().stream().anyMatch(method -> method.name().equals("hidden")));
		assertEquals("java.lang.String", recipe.methods().stream().filter(method -> method.name().equals("time")).findFirst().orElseThrow().parameters().getFirst().type().name());
		assertEquals("200", recipe.methods().stream().filter(method -> method.name().equals("cookingTime")).findFirst().orElseThrow().parameters().getFirst().defaultValue());
		assertEquals("cookingTime", recipe.methods().stream().filter(method -> method.name().equals("duration")).findFirst().orElseThrow().primaryName());
		assertEquals(0, calls.get());
		var rendered = RecipeTypingGenerator.render(snapshot);
		assertEquals(rendered, RecipeTypingGenerator.render(RecipeTypingGenerator.snapshot(storage, false)));
		assertThrows(UnsupportedOperationException.class, () -> snapshot.namespaces().get("test").put("extra", "test:recipe"));
	}

	@Test
	void automaticConstructorsRespectExcludedKeysAndUnknownSchemasUseJson() {
		var storage = storage();
		var required = StringComponent.STRING.outputKey("result");
		var optional = NumberComponent.INT.otherKey("time").optional(100);
		var excluded = StringComponent.STRING.otherKey("internal").optional("value").exclude();
		storage.namespace("test").register("known", new RecipeSchema(required, optional, excluded)).register("unknown", UnknownRecipeSchema.SCHEMA);
		var recipes = RecipeTypingGenerator.snapshot(storage, false).recipes();
		assertEquals(List.of(1, 2), recipes.get("test:known").constructors().stream().map(method -> method.parameters().size()).toList());
		assertEquals(List.of(0, 1, 1), recipes.get("test:unknown").constructors().stream().map(method -> method.parameters().size()).toList());
		assertEquals(List.of("java.util.Map", "com.google.gson.JsonObject"), recipes.get("test:unknown").constructors().stream().filter(method -> !method.parameters().isEmpty()).map(method -> method.parameters().getFirst().type().name()).toList());
		assertTrue(recipes.get("test:unknown").methods().isEmpty());
	}

	@Test
	void inputTypesRetainUnionArrayAndGenericStructure() {
		var union = RecipeTypingGenerator.input(TypeInfo.STRING.or(TypeInfo.INT));
		assertEquals("union", union.kind());
		assertEquals(List.of("java.lang.String", "java.lang.Integer"), union.arguments().stream().map(RecipeTypingGenerator.InputType::name).toList());
		var map = RecipeTypingGenerator.input(TypeInfo.RAW_MAP.withParams(TypeInfo.STRING, TypeInfo.RAW_LIST.withParams(TypeInfo.INT.asArray())));
		assertEquals("java.util.List", map.arguments().get(1).name());
		assertEquals("array", map.arguments().get(1).arguments().getFirst().kind());
	}
}
