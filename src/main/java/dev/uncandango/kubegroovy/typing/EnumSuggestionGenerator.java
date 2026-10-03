package dev.uncandango.kubegroovy.typing;

import dev.uncandango.kubegroovy.plugin.TypeSuggestionCatalog;
import dev.uncandango.kubegroovy.script.type.EnumTypeInfo;
import net.minecraft.util.StringRepresentable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;

/** Exports only enum names that cannot be reconstructed from constant declarations in PSI. */
public final class EnumSuggestionGenerator {
	private EnumSuggestionGenerator() {
	}

	public static TypeSuggestionCatalog withOverrides(TypeSuggestionCatalog catalog, Collection<Class<?>> classes) {
		var values = new LinkedHashMap<>(catalog.values());
		for (var type : classes.stream().distinct().sorted(Comparator.comparing(Class::getName)).toList()) {
			if (!type.isEnum() || !StringRepresentable.class.isAssignableFrom(type)) continue;
			var constants = type.getEnumConstants();
			var names = Arrays.stream(constants).map(EnumTypeInfo::getName)
				.map(name -> name.toLowerCase(Locale.ROOT)).toList();
			var staticNames = Arrays.stream(constants).map(value -> ((Enum<?>) value).name().toLowerCase(Locale.ROOT)).toList();
			if (!names.equals(staticNames)) {
				// Explicit addon providers remain authoritative, including their ordering and duplicates.
				values.putIfAbsent(TypeSuggestionCatalog.className(type), names);
			}
		}
		return new TypeSuggestionCatalog(values, catalog.delegates(), catalog.genericDelegates());
	}
}
