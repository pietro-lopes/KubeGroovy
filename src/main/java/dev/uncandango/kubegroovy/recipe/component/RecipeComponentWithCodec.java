package dev.uncandango.kubegroovy.recipe.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.uncandango.kubegroovy.recipe.schema.RecipeSchemaStorage;
import dev.uncandango.kubegroovy.util.Cast;
import dev.uncandango.kubegroovy.util.OpsContainer;
import net.minecraft.resources.ResourceKey;

import java.lang.reflect.Modifier;
import java.util.Optional;

public record RecipeComponentWithCodec<T>(RecipeComponent<T> parent, Codec<T> codec, Optional<CodecReference> codecReference, Optional<RecipeCodecConversion> conversion) implements RecipeComponentWithParent<T> {
	public static final ResourceKey<RecipeComponentType<?>> TYPE = RecipeComponentType.builtin("with_codec");
	public static final MapCodec<RecipeComponentWithCodec<?>> MAP_CODEC = RecordCodecBuilder.<Definition>mapCodec(instance -> instance.group(
		RecipeSchemaStorage.COMPONENT_CODEC.fieldOf("component").forGetter(Definition::component),
		CodecReference.CODEC.fieldOf("codec").forGetter(Definition::codec),
		RecipeCodecConversion.CODEC.optionalFieldOf("conversion").forGetter(Definition::conversion)
	).apply(instance, Definition::new)).flatXmap(Definition::resolve, value -> value.codecReference()
		.<DataResult<Definition>>map(reference -> DataResult.success(new Definition(value.parent(), reference, value.conversion())))
		.orElseGet(() -> DataResult.error(() -> "Custom codec has no reflective field reference")));

	public RecipeComponentWithCodec(RecipeComponent<T> parent, Codec<T> codec) {
		this(parent, codec, Optional.empty(), Optional.empty());
	}

	public RecipeComponentWithCodec(RecipeComponent<T> parent, Codec<T> codec, Optional<CodecReference> codecReference) {
		this(parent, codec, codecReference, Optional.empty());
	}

	/// Resolves a static codec field without exposing its reflective handle.
	public static <T> DataResult<RecipeComponentWithCodec<T>> resolve(RecipeComponent<T> parent, CodecReference reference) {
		return resolve(parent, reference, Optional.empty());
	}

	public static <T> DataResult<RecipeComponentWithCodec<T>> resolve(RecipeComponent<T> parent, CodecReference reference, Optional<RecipeCodecConversion> conversion) {
		return reference.resolve().flatMap(value -> conversion
			.map(mapping -> mapping.<T>adapt(Cast.to(parent.typeInfo().asClass()), value))
			.orElseGet(() -> DataResult.success(Cast.<Codec<T>>to(value))))
			.map(value -> new RecipeComponentWithCodec<>(parent, value, Optional.of(reference), conversion));
	}

	public record CodecReference(String className, String field) {
		public static final Codec<CodecReference> CODEC = Codec.withAlternative(RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("class").forGetter(CodecReference::className),
			Codec.STRING.optionalFieldOf("field", "CODEC").forGetter(CodecReference::field)
		).apply(instance, CodecReference::new)), Codec.STRING, CodecReference::new);

		public CodecReference(String className) {
			this(className, "CODEC");
		}

		private DataResult<Codec<?>> resolve() {
			var target = className + "#" + field;
			try {
				var owner = Class.forName(className, false, RecipeComponentWithCodec.class.getClassLoader());
				var member = owner.getDeclaredField(field);
				if (!Modifier.isStatic(member.getModifiers())) {
					return DataResult.error(() -> "Codec field " + target + " must be static");
				}
				if (!Codec.class.isAssignableFrom(member.getType()) && !MapCodec.class.isAssignableFrom(member.getType())) {
					return DataResult.error(() -> "Field " + target + " must declare a Codec or MapCodec");
				}
				if (!member.trySetAccessible()) {
					return DataResult.error(() -> "Codec field " + target + " is inaccessible");
				}
				return switch (member.get(null)) {
					case Codec<?> value -> DataResult.success(value);
					case MapCodec<?> value -> DataResult.success(value.codec());
					case null -> DataResult.error(() -> "Codec field " + target + " is null");
					default -> DataResult.error(() -> "Field " + target + " does not contain a Codec or MapCodec");
				};
			} catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
				return DataResult.error(() -> "Failed to resolve codec field " + target + ": " + ex);
			}
		}
	}

	private record Definition(RecipeComponent<?> component, CodecReference codec, Optional<RecipeCodecConversion> conversion) {
		private DataResult<RecipeComponentWithCodec<?>> resolve() {
			return RecipeComponentWithCodec.resolve(component, codec, conversion).map(value -> value);
		}
	}

	@Override
	public ResourceKey<RecipeComponentType<?>> type() {
		return codecReference.isPresent() ? TYPE : parent.type();
	}

	@Override
	public RecipeComponent<T> parentComponent() {
		return parent;
	}

	@Override
	public Codec<T> codec() {
		return codec;
	}

	@Override
	public String toString(OpsContainer ops, T value) {
		return parent.toString(ops, value);
	}

	@Override
	public String toString() {
		return parent + "{custom_codec}";
	}
}