package dev.uncandango.kubegroovy.plugin;

import dev.uncandango.kubegroovy.script.KubeGroovyContext;
import dev.uncandango.kubegroovy.script.type.TypeInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/// Collects type suggestion providers and the wrapper rules consumed by LivePlugin.
public final class TypeSuggestionRegistry {
	private final Map<TypeInfo, List<ProviderRegistration>> providers = new LinkedHashMap<>();
	private final List<DelegateRegistration> delegates = new ArrayList<>();
	private final Map<String, Integer> genericDelegates = new LinkedHashMap<>();
	private String currentOwner = "unknown plugin";

	public void register(TypeInfo type, TypeSuggestionProvider provider) {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(provider, "provider");
		if (type == TypeInfo.NONE) throw new IllegalArgumentException("A suggestion provider needs a concrete type");
		providers.computeIfAbsent(type, ignored -> new ArrayList<>())
			.add(new ProviderRegistration(currentOwner, type, provider));
	}

	public void register(Class<?> type, TypeSuggestionProvider provider) {
		register(TypeInfo.of(Objects.requireNonNull(type, "type")), provider);
	}

	public void registerModifierFor(TypeInfo elementType, Class<?> modifierClass, TypeSuggestionProvider provider) {
		Objects.requireNonNull(elementType, "elementType");
		Objects.requireNonNull(modifierClass, "modifierClass");
		Objects.requireNonNull(provider, "provider");
		if (elementType == TypeInfo.NONE) throw new IllegalArgumentException("A modifier needs a concrete element type");
		if (modifierClass.getTypeParameters().length != 1) {
			throw new IllegalArgumentException("Modifier class must have exactly one type parameter: " + modifierClass.getName());
		}
		var modifiedType = TypeInfo.of(modifierClass).withParams(elementType);
		providers.computeIfAbsent(modifiedType, ignored -> new ArrayList<>())
			.add(new ProviderRegistration(currentOwner, elementType, provider));
	}

	public void registerModifierFor(Class<?> elementType, Class<?> modifierClass, TypeSuggestionProvider provider) {
		registerModifierFor(TypeInfo.of(Objects.requireNonNull(elementType, "elementType")), modifierClass, provider);
	}

	public void registerDelegate(TypeInfo sourceType, TypeInfo targetType) {
		registerDelegate(sourceType, targetType, "");
	}

	public void registerDelegate(Class<?> sourceType, TypeInfo targetType) {
		registerDelegate(TypeInfo.of(sourceType), targetType);
	}

	public void registerDelegate(TypeInfo sourceType, Class<?> targetType) {
		registerDelegate(sourceType, TypeInfo.of(targetType));
	}

	public void registerDelegate(TypeInfo sourceType, TypeInfo targetType, String prefix) {
		Objects.requireNonNull(sourceType, "sourceType");
		Objects.requireNonNull(targetType, "targetType");
		Objects.requireNonNull(prefix, "prefix");
		if (sourceType == TypeInfo.NONE || targetType == TypeInfo.NONE) {
			throw new IllegalArgumentException("Delegate source and target must be concrete types");
		}
		delegates.add(new DelegateRegistration(TypeSuggestionCatalog.typeName(sourceType),
			TypeSuggestionCatalog.typeName(targetType), prefix));
	}

	public void registerDelegate(Class<?> sourceType, Class<?> targetType) {
		registerDelegate(TypeInfo.of(sourceType), TypeInfo.of(targetType));
	}

	public void registerDelegate(Class<?> sourceType, Class<?> targetType, String prefix) {
		registerDelegate(TypeInfo.of(sourceType), TypeInfo.of(targetType), prefix);
	}

	public void registerDelegate(Class<?> sourceType, TypeInfo targetType, String prefix) {
		registerDelegate(TypeInfo.of(sourceType), targetType, prefix);
	}

	public void registerDelegate(TypeInfo sourceType, Class<?> targetType, String prefix) {
		registerDelegate(sourceType, TypeInfo.of(targetType), prefix);
	}

	/// Delegates a one-parameter wrapper to its contained type.
	public void registerDelegate(Class<?> wrapperClass) {
		Objects.requireNonNull(wrapperClass, "wrapperClass");
		int parameterCount = wrapperClass.getTypeParameters().length;
		if (parameterCount != 1) {
			throw new IllegalArgumentException("Generic delegate class must have exactly one type parameter: " + wrapperClass.getName());
		}
		genericDelegates.putIfAbsent(TypeSuggestionCatalog.className(wrapperClass), 0);
	}

	/// Internal attribution used while plugin hooks are invoked during generation.
	public void setCurrentOwner(String owner) {
		currentOwner = Objects.requireNonNull(owner, "owner");
	}

	public TypeSuggestionCatalog resolve(KubeGroovyContext cx) {
		Objects.requireNonNull(cx, "cx");
		var values = new LinkedHashMap<String, List<String>>();
		for (var entry : providers.entrySet()) {
			var typeName = TypeSuggestionCatalog.typeName(entry.getKey());
			var resolvedValues = values.computeIfAbsent(typeName, ignored -> new ArrayList<>());
			for (var registration : entry.getValue()) {
				try (Stream<String> stream = Objects.requireNonNull(
					registration.provider().getSuggestions(cx, registration.providerType()),
					"provider returned a null stream")) {
					stream.sequential().forEachOrdered(value -> resolvedValues.add(Objects.requireNonNull(value,
						"provider returned a null suggestion")));
				} catch (Throwable failure) {
					throw new IllegalStateException("Type suggestion provider from " + registration.owner()
						+ " failed for " + typeName, failure);
				}
			}
		}
		return new TypeSuggestionCatalog(values, delegates.stream()
			.map(delegate -> new TypeSuggestionCatalog.Delegate(delegate.source(), delegate.target(), delegate.prefix()))
			.toList(), genericDelegates);
	}

	private record ProviderRegistration(String owner, TypeInfo providerType, TypeSuggestionProvider provider) {
	}

	private record DelegateRegistration(String source, String target, String prefix) {
	}
}
