package dev.uncandango.kubegroovy.recipetest;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import dev.uncandango.kubegroovy.recipe.component.RecipeComponentWithCodec;
import dev.uncandango.kubegroovy.recipe.component.StringComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecipeComponentWithCodecTests {
	private static final class Fields {
		private static final Codec<String> CODEC = Codec.STRING;
		private static final MapCodec<String> RESULT_CODEC = Codec.STRING.fieldOf("item");
	}

	private static final class MapFields {
		private static final MapCodec<String> CODEC = Codec.STRING.fieldOf("item");
	}

	private static final class NullFields {
		private static final Codec<String> CODEC = null;
	}

	private static final class WrongFields {
		private static final String CODEC = "no";
	}

	private static final class UntypedFields {
		private static final Object CODEC = Codec.STRING;
	}

	private static final class InstanceFields {
		private final Codec<String> CODEC = Codec.STRING;
	}

	private static final class MissingFields {
	}

	private static final class InitializationTracker {
		private static boolean initialized;
	}

	private static final class UnsupportedFields {
		private static final Object CODEC = Codec.STRING;

		static {
			InitializationTracker.initialized = true;
		}
	}

	@Test
	void rejectsUntypedFieldBeforeInitializingItsClass() {
		assertTrue(resolve(UnsupportedFields.class).isError());
		assertFalse(InitializationTracker.initialized);
	}

	@Test
	void resolvesPrivateCodecAndDelegatesBehavior() {
		var wrapper = resolve(Fields.class).getOrThrow();
		assertSame(Fields.CODEC, wrapper.codec());
		assertSame(StringComponent.STRING, wrapper.parentComponent());
		assertSame(StringComponent.STRING.typeInfo(), wrapper.typeInfo());
		assertTrue(wrapper.isEmpty(""));
		assertEquals(RecipeComponentWithCodec.TYPE, wrapper.type());
		assertEquals("apple", wrapper.codec().parse(JsonOps.INSTANCE, new JsonPrimitive("apple")).getOrThrow());
	}

	@Test
	void adaptsPrivateMapCodecInBothDirections() {
		var wrapper = resolve(MapFields.class).getOrThrow();
		var json = new JsonObject();
		json.addProperty("item", "apple");
		assertEquals("apple", wrapper.codec().parse(JsonOps.INSTANCE, json).getOrThrow());
		assertEquals(json, wrapper.codec().encodeStart(JsonOps.INSTANCE, "apple").getOrThrow());
	}

	@Test
	void rejectsInvalidReferencesWithFieldDiagnostics() {
		for (var owner : new Class<?>[]{NullFields.class, WrongFields.class, UntypedFields.class, InstanceFields.class, MissingFields.class}) {
			var result = resolve(owner);
			assertTrue(result.isError(), owner.getName());
			assertTrue(result.error().orElseThrow().message().contains(owner.getName() + "#CODEC"));
		}
		var missingClass = RecipeComponentWithCodec.resolve(StringComponent.STRING,
			new RecipeComponentWithCodec.CodecReference("missing.mod.Serializer"));
		assertTrue(missingClass.isError());
	}

	@Test
	void defaultsFieldAndAllowsExplicitOverride() {
		var object = new JsonObject();
		object.addProperty("class", Fields.class.getName());
		var reference = RecipeComponentWithCodec.CodecReference.CODEC.parse(JsonOps.INSTANCE, object).getOrThrow();
		assertEquals("CODEC", reference.field());
		assertEquals(reference, RecipeComponentWithCodec.CodecReference.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(Fields.class.getName())).getOrThrow());
		object.addProperty("field", "RESULT_CODEC");
		var explicit = RecipeComponentWithCodec.CodecReference.CODEC.parse(JsonOps.INSTANCE, object).getOrThrow();
		assertEquals("RESULT_CODEC", explicit.field());
		var wrapper = RecipeComponentWithCodec.resolve(StringComponent.STRING, explicit).getOrThrow();
		var value = new JsonObject();
		value.addProperty("item", "apple");
		assertEquals(value, wrapper.codec().encodeStart(JsonOps.INSTANCE, "apple").getOrThrow());
	}

	@Test
	void preservesJavaOnlyConstructor() {
		var wrapper = new RecipeComponentWithCodec<>(StringComponent.STRING, Codec.STRING);
		assertSame(StringComponent.STRING.type(), wrapper.type());
		assertTrue(wrapper.codecReference().isEmpty());
	}

	private static DataResult<RecipeComponentWithCodec<String>> resolve(Class<?> owner) {
		return RecipeComponentWithCodec.resolve(StringComponent.STRING,
			new RecipeComponentWithCodec.CodecReference(owner.getName()));
	}
}
