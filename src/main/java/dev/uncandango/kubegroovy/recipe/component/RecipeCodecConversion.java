package dev.uncandango.kubegroovy.recipe.component;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.uncandango.kubegroovy.script.type.TypeConsolidator;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import dev.uncandango.kubegroovy.util.Cast;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// A bidirectional projection between a component's value class and a foreign codec's value class.
public record RecipeCodecConversion(String className, Map<String, Property> properties, Optional<List<String>> parentConstructor, Optional<List<String>> codecConstructor) {
	public static final Codec<RecipeCodecConversion> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("class").forGetter(RecipeCodecConversion::className),
		Codec.unboundedMap(Codec.STRING, Property.CODEC).optionalFieldOf("properties", Map.of()).forGetter(RecipeCodecConversion::properties),
		Codec.STRING.listOf().optionalFieldOf("parent_constructor").forGetter(RecipeCodecConversion::parentConstructor),
		Codec.STRING.listOf().optionalFieldOf("codec_constructor").forGetter(RecipeCodecConversion::codecConstructor)
	).apply(instance, RecipeCodecConversion::new));

	public enum Access { AUTO, FIELD, METHOD }

	public record Property(String name, Access access) {
		private record Explicit(Optional<String> field, Optional<String> method) {
			private static final Codec<Explicit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.optionalFieldOf("field").forGetter(Explicit::field),
				Codec.STRING.optionalFieldOf("method").forGetter(Explicit::method)
			).apply(instance, Explicit::new));
		}

		public static final Codec<Property> CODEC = Codec.either(Codec.STRING, Explicit.CODEC).flatXmap(
			value -> value.map(name -> DataResult.success(new Property(name, Access.AUTO)), explicit -> {
				if (explicit.field().isPresent() == explicit.method().isPresent()) {
					return DataResult.error(() -> "A conversion property requires exactly one of field or method");
				}
				return DataResult.success(explicit.field().map(name -> new Property(name, Access.FIELD))
					.orElseGet(() -> new Property(explicit.method().orElseThrow(), Access.METHOD)));
			}),
			property -> DataResult.success(property.access() == Access.AUTO ? Either.left(property.name()) : Either.right(
				new Explicit(property.access() == Access.FIELD ? Optional.of(property.name()) : Optional.empty(),
					property.access() == Access.METHOD ? Optional.of(property.name()) : Optional.empty())))
		);
	}

	public <T> DataResult<Codec<T>> adapt(Class<T> parent, Codec<?> codec) {
		try {
			var foreign = Class.forName(className, false, RecipeCodecConversion.class.getClassLoader());
			var parentPlan = constructor(parent, parentConstructor, Map.of(), Map.of());
			var names = parentPlan.names();
			if (!names.containsAll(properties.keySet())) {
				throw new IllegalArgumentException("Unknown parent properties: " + properties.keySet());
			}
			var parentReaders = new LinkedHashMap<String, Reader>();
			var foreignReaders = new LinkedHashMap<String, Reader>();
			var aliases = new LinkedHashMap<String, String>();
			for (var name : names) {
				var property = properties.getOrDefault(name, new Property(name, Access.AUTO));
				if (aliases.put(property.name(), name) != null) {
					throw new IllegalArgumentException("Duplicate foreign property: " + property.name());
				}
				parentReaders.put(name, reader(parent, new Property(name, Access.AUTO)));
				foreignReaders.put(name, reader(foreign, property));
			}
			var foreignPlan = constructor(foreign, codecConstructor, aliases, foreignReaders);
			if (names.size() != foreignPlan.names().size() || !names.containsAll(foreignPlan.names())) {
				throw new IllegalArgumentException("Constructors must consume the same mapped properties");
			}
			checkTypes(parentPlan, foreignReaders);
			checkTypes(foreignPlan, parentReaders);
			Codec<Object> source = Cast.to(codec);
			return DataResult.success(source.flatXmap(
				value -> convert(value, foreign, parentPlan, foreignReaders).map(parent::cast),
				value -> convert(value, parent, foreignPlan, parentReaders)
			));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
			return DataResult.error(() -> "Failed to resolve conversion " + parent.getName() + " <-> " + className + ": " + ex);
		}
	}

	private static DataResult<Object> convert(Object value, Class<?> expected, Construction target, Map<String, Reader> readers) {
		try {
			if (!expected.isInstance(value)) {
				throw new IllegalArgumentException("Expected " + expected.getName() + ", got " + (value == null ? "null" : value.getClass().getName()));
			}
			var arguments = new Object[target.names().size()];
			for (int i = 0; i < arguments.length; i++) {
				arguments[i] = readers.get(target.names().get(i)).read(value);
			}
			return DataResult.success(target.constructor().newInstance(arguments));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
			return DataResult.error(() -> "Failed to convert " + expected.getName() + " to " + target.constructor().getDeclaringClass().getName() + ": " + ex);
		}
	}

	private record Reader(Class<?> type, Optional<Method> method, Optional<Field> field) {
		Object read(Object value) throws ReflectiveOperationException {
			return method.isPresent() ? method.get().invoke(value) : field.orElseThrow().get(value);
		}
	}

	private static Reader reader(Class<?> owner, Property property) throws ReflectiveOperationException {
		if (property.access() != Access.FIELD) {
			for (var current = owner; current != null && current != Object.class; current = current.getSuperclass()) {
				try {
					var method = current.getDeclaredMethod(property.name());
					if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() == void.class || !method.trySetAccessible()) {
						throw new IllegalArgumentException("Property accessor must be accessible, non-static, and return a value: " + method);
					}
					return new Reader(TypeInfo.of(method.getGenericReturnType()).consolidate(TypeConsolidator.getMapping(owner)).asClass(), Optional.of(method), Optional.empty());
				} catch (NoSuchMethodException ignored) {
				}
			}
			if (property.access() == Access.METHOD) {
				throw new NoSuchMethodException(owner.getName() + "#" + property.name());
			}
		}
		for (var current = owner; current != null && current != Object.class; current = current.getSuperclass()) {
			try {
				var field = current.getDeclaredField(property.name());
				if (Modifier.isStatic(field.getModifiers()) || !field.trySetAccessible()) {
					throw new IllegalArgumentException("Property field must be accessible and non-static: " + field);
				}
				return new Reader(TypeInfo.of(field.getGenericType()).consolidate(TypeConsolidator.getMapping(owner)).asClass(), Optional.empty(), Optional.of(field));
			} catch (NoSuchFieldException ignored) {
			}
		}
		throw new NoSuchFieldException(owner.getName() + "#" + property.name());
	}

	private record Construction(Constructor<?> constructor, List<String> names) {
	}

	private static Construction constructor(Class<?> owner, Optional<List<String>> explicit, Map<String, String> aliases, Map<String, Reader> readers) throws ReflectiveOperationException {
		var candidates = new ArrayList<Construction>();
		for (var constructor : owner.getDeclaredConstructors()) {
			List<String> names;
			if (explicit.isPresent()) {
				names = explicit.get();
			} else if (owner.isRecord()) {
				var components = owner.getRecordComponents();
				if (!Arrays.equals(constructor.getParameterTypes(), Arrays.stream(components).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new))) {
					continue;
				}
				names = Arrays.stream(components).map(component -> aliases.getOrDefault(component.getName(), component.getName())).toList();
			} else {
				if (!Arrays.stream(constructor.getParameters()).allMatch(java.lang.reflect.Parameter::isNamePresent)) {
					continue;
				}
				names = Arrays.stream(constructor.getParameters()).map(parameter -> aliases.getOrDefault(parameter.getName(), parameter.getName())).toList();
			}
			if (names.isEmpty() || names.size() != constructor.getParameterCount() || names.stream().distinct().count() != names.size()) {
				continue;
			}
			boolean compatible = true;
			for (int i = 0; i < names.size(); i++) {
				var name = names.get(i);
				var foreignName = aliases.entrySet().stream().filter(entry -> entry.getValue().equals(name)).map(Map.Entry::getKey).findFirst().orElse(name);
				try {
					var resolved = readers.get(name);
					var type = resolved == null ? reader(owner, new Property(foreignName, Access.AUTO)).type() : resolved.type();
					compatible &= boxed(constructor.getParameterTypes()[i]).isAssignableFrom(boxed(type));
				} catch (NoSuchFieldException | NoSuchMethodException ex) {
					compatible = false;
				}
			}
			if (compatible) {
				candidates.add(new Construction(constructor, List.copyOf(names)));
			}
		}
		if (candidates.size() != 1) {
			throw new IllegalArgumentException("Cannot identify a unique constructor for " + owner.getName() + "; specify constructor property order (found " + candidates.size() + " candidates)");
		}
		var result = candidates.getFirst();
		if (!result.constructor().trySetAccessible()) {
			throw new IllegalArgumentException("Inaccessible constructor: " + result.constructor());
		}
		return result;
	}

	private static void checkTypes(Construction target, Map<String, Reader> readers) {
		for (int i = 0; i < target.names().size(); i++) {
			var reader = readers.get(target.names().get(i));
			if (reader == null || !boxed(target.constructor().getParameterTypes()[i]).isAssignableFrom(boxed(reader.type()))) {
				throw new IllegalArgumentException("Incompatible property " + target.names().get(i) + " for " + target.constructor());
			}
		}
	}

	private static Class<?> boxed(Class<?> type) {
		return type.isPrimitive() ? switch (type.getName()) {
			case "boolean" -> Boolean.class;
			case "byte" -> Byte.class;
			case "short" -> Short.class;
			case "char" -> Character.class;
			case "int" -> Integer.class;
			case "long" -> Long.class;
			case "float" -> Float.class;
			case "double" -> Double.class;
			default -> Void.class;
		} : type;
	}
}