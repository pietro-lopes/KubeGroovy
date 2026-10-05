package dev.uncandango.kubegroovy.recipetest;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.uncandango.kubegroovy.recipe.component.RecipeCodecConversion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RecipeCodecConversionTests {
	private record Parent(String ingredient, int count) {}
	private record Foreign(String ingredient, int amount) {
		private static final Codec<Foreign> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("ingredient").forGetter(Foreign::ingredient),
			Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("amount", 1).forGetter(Foreign::amount)
		).apply(instance, Foreign::new));
	}
	private record Same(String ingredient, int count) {}
	private record Wrong(String ingredient, String amount) {}
	private record Rejecting(String ingredient, int amount) {
		Rejecting { throw new IllegalArgumentException("Rejected value"); }
	}
	private record FailingReader(String ingredient, int amount) {
		@Override public int amount() { throw new IllegalStateException("Cannot read amount"); }
	}
	private static final class Fields {
		private final String ingredient;
		private final int amount;
		private Fields(String ingredient, int amount) { this.ingredient = ingredient; this.amount = amount; }
		private String amount() { return "wrong accessor"; }
	}
	private static final class Ambiguous {
		private final String ingredient;
		private final int amount;
		private Ambiguous(String ingredient, int amount) { this.ingredient = ingredient; this.amount = amount; }
		private Ambiguous(Object ingredient, int amount) { this.ingredient = ingredient.toString(); this.amount = amount; }
	}
	private static final class NoParameterNames {
		private final String ingredient;
		private final int amount;
		private NoParameterNames(String ingredient, int amount) { this.ingredient = ingredient; this.amount = amount; }
	}
	private static final class StaticProperty {
		private final String ingredient;
		private static final int amount = 1;
		private StaticProperty(String ingredient, int amount) { this.ingredient = ingredient; }
	}

	private static abstract class GenericBase<S extends CharSequence> {
		protected final S ingredient;
		protected final int amount;
		private GenericBase(S ingredient, int amount) { this.ingredient = ingredient; this.amount = amount; }
		public S getIngredient() { return ingredient; }
	}
	private static final class GenericForeign extends GenericBase<String> {
		private GenericForeign(String ingredient, int amount) { super(ingredient, amount); }
	}
	@Test
	void aliasesRecordsAndPreservesForeignDefaultsAndValidation() {
		var conversion = mapping(Foreign.class, Map.of("count", property("amount")), Optional.empty());
		var codec = conversion.adapt(Parent.class, Foreign.CODEC).getOrThrow();
		assertEquals(new Parent("apple", 1), codec.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"ingredient\":\"apple\"}")).getOrThrow());
		var value = new Parent("apple", 3);
		var json = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
		assertEquals(3, json.getAsJsonObject().get("amount").getAsInt());
		assertEquals(value, codec.parse(JsonOps.INSTANCE, json).getOrThrow());
		assertTrue(codec.encodeStart(JsonOps.INSTANCE, new Parent("apple", 0)).isError());
		assertTrue(codec.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"ingredient\":\"apple\",\"amount\":0}")).isError());
		var definition = RecipeCodecConversion.CODEC.encodeStart(JsonOps.INSTANCE, conversion).getOrThrow();
		assertEquals(conversion, RecipeCodecConversion.CODEC.parse(JsonOps.INSTANCE, definition).getOrThrow());
	}

	@Test
	void defaultsToSameNames() {
		var source = Codec.STRING.xmap(value -> new Same(value, 2), Same::ingredient);
		var codec = mapping(Same.class, Map.of(), Optional.empty()).adapt(Parent.class, source).getOrThrow();
		assertEquals(new Parent("apple", 2), codec.parse(JsonOps.INSTANCE, JsonParser.parseString("\"apple\"")).getOrThrow());
		assertEquals("apple", codec.encodeStart(JsonOps.INSTANCE, new Parent("apple", 2)).getOrThrow().getAsString());
	}

	@Test
	void explicitFieldAndConstructorOrderWorkWithoutParameterMetadata() {
		var conversion = mapping(Fields.class, Map.of("count", new RecipeCodecConversion.Property("amount", RecipeCodecConversion.Access.FIELD)), Optional.of(List.of("ingredient", "count")));
		var source = Codec.STRING.xmap(value -> new Fields(value, 4), value -> value.ingredient);
		var codec = conversion.adapt(Parent.class, source).getOrThrow();
		assertEquals(new Parent("apple", 4), codec.parse(JsonOps.INSTANCE, JsonParser.parseString("\"apple\"")).getOrThrow());
		assertEquals("apple", codec.encodeStart(JsonOps.INSTANCE, new Parent("apple", 4)).getOrThrow().getAsString());
	}

	@Test
	void rejectsAmbiguousConstructorsAndIncompatibleOrUnknownProperties() {
		assertTrue(mapping(Ambiguous.class, Map.of("count", property("amount")), Optional.of(List.of("ingredient", "count"))).adapt(Parent.class, Codec.STRING).isError());
		assertTrue(mapping(Wrong.class, Map.of("count", property("amount")), Optional.empty()).adapt(Parent.class, Codec.STRING).isError());
		assertTrue(mapping(Foreign.class, Map.of("unknown", property("amount")), Optional.empty()).adapt(Parent.class, Foreign.CODEC).isError());
		assertTrue(mapping(Foreign.class, Map.of("count", property("ingredient")), Optional.empty()).adapt(Parent.class, Foreign.CODEC).isError());
	}

	@Test
	void returnsCodecErrorsForWrongValuesFailingConstructorsAndAccessors() {
		var wrong = mapping(Foreign.class, Map.of("count", property("amount")), Optional.empty()).adapt(Parent.class, Codec.STRING).getOrThrow();
		assertTrue(wrong.parse(JsonOps.INSTANCE, JsonParser.parseString("\"apple\"")).isError());
		var rejecting = mapping(Rejecting.class, Map.of("count", property("amount")), Optional.empty()).adapt(Parent.class, Codec.STRING).getOrThrow();
		assertTrue(rejecting.encodeStart(JsonOps.INSTANCE, new Parent("apple", 3)).isError());
		var failing = mapping(FailingReader.class, Map.of("count", property("amount")), Optional.empty())
			.adapt(Parent.class, Codec.STRING.xmap(value -> new FailingReader(value, 3), FailingReader::ingredient)).getOrThrow();
		assertTrue(failing.parse(JsonOps.INSTANCE, JsonParser.parseString("\"apple\"")).isError());
	}

	@Test
	void rejectsInvalidExplicitAccessConfiguration() {
		assertTrue(RecipeCodecConversion.Property.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).isError());
		assertTrue(RecipeCodecConversion.Property.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"field\":\"amount\",\"method\":\"amount\"}")).isError());
	}

	@Test
	void requiresOrderWhenParameterNamesAreAbsent() {
		assertFalse(NoParameterNames.class.getDeclaredConstructors()[0].getParameters()[0].isNamePresent());
		var source = Codec.STRING.xmap(value -> new NoParameterNames(value, 3), value -> value.ingredient);
		assertTrue(mapping(NoParameterNames.class, Map.of("count", property("amount")), Optional.empty()).adapt(Parent.class, source).isError());
		var codec = mapping(NoParameterNames.class, Map.of("count", property("amount")), Optional.of(List.of("ingredient", "count"))).adapt(Parent.class, source).getOrThrow();
		assertEquals(new Parent("apple", 3), codec.parse(JsonOps.INSTANCE, JsonParser.parseString("\"apple\"")).getOrThrow());
	}

	@Test
	void permitsExplicitMethodAndRejectsStaticReaders() {
		var codec = mapping(Foreign.class, Map.of("count", new RecipeCodecConversion.Property("amount", RecipeCodecConversion.Access.METHOD)), Optional.empty())
			.adapt(Parent.class, Foreign.CODEC).getOrThrow();
		assertEquals(new Parent("apple", 1), codec.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"ingredient\":\"apple\"}")).getOrThrow());
		assertTrue(mapping(StaticProperty.class, Map.of("count", property("amount")), Optional.of(List.of("ingredient", "count"))).adapt(Parent.class, Codec.STRING).isError());
	}

	@Test
	void resolvesInheritedGenericFieldsAndMethodsAgainstConcreteClass() {
		var source = Codec.STRING.xmap(value -> new GenericForeign(value, 4), value -> value.ingredient);
		for (var access : List.of(RecipeCodecConversion.Access.FIELD, RecipeCodecConversion.Access.METHOD)) {
			var ingredient = new RecipeCodecConversion.Property(access == RecipeCodecConversion.Access.FIELD ? "ingredient" : "getIngredient", access);
			var conversion = mapping(GenericForeign.class, Map.of("ingredient", ingredient, "count", property("amount")),
				Optional.of(List.of("ingredient", "count")));
			var codec = conversion.adapt(Parent.class, source).getOrThrow();
			var value = new Parent("apple", 4);
			assertEquals(value, codec.parse(JsonOps.INSTANCE, JsonParser.parseString("\"apple\"")).getOrThrow());
			assertEquals("apple", codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow().getAsString());
		}
	}
	private static RecipeCodecConversion.Property property(String name) {
		return new RecipeCodecConversion.Property(name, RecipeCodecConversion.Access.AUTO);
	}

	private static RecipeCodecConversion mapping(Class<?> owner, Map<String, RecipeCodecConversion.Property> properties, Optional<List<String>> constructor) {
		return new RecipeCodecConversion(owner.getName(), properties, Optional.empty(), constructor);
	}
}