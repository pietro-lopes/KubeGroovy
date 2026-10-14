package dev.uncandango.kubegroovy.typingtest;

import dev.uncandango.kubegroovy.plugin.TypeSuggestionCatalog;
import dev.uncandango.kubegroovy.typing.EnumSuggestionGenerator;
import net.minecraft.util.StringRepresentable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EnumSuggestionGeneratorTests {
	enum Plain { FIRST, SECOND }

	enum Matching implements StringRepresentable {
		FIRST, SECOND;

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	enum Custom implements StringRepresentable {
		FIRST, SECOND;

		@Override
		public String getSerializedName() {
			return this == FIRST ? "different_name" : "";
		}
	}

	@Test
	void onlyDifferingEnumsAreExportedWithCompleteRuntimeNames() {
		var result = EnumSuggestionGenerator.withOverrides(TypeSuggestionCatalog.empty(),
			List.of(Plain.class, Matching.class, Custom.class, Custom.class, String.class));
		assertEquals(Map.of(TypeSuggestionCatalog.className(Custom.class), List.of("different_name", "second")), result.values());
		assertFalse(result.values().containsKey(TypeSuggestionCatalog.className(Matching.class)));
	}

	@Test
	void explicitProvidersAndDelegatesArePreserved() {
		var catalog = new TypeSuggestionCatalog(Map.of(TypeSuggestionCatalog.className(Custom.class), List.of("addon", "addon")),
			List.of(new TypeSuggestionCatalog.Delegate("example.Wrapper", TypeSuggestionCatalog.className(Custom.class), "#")),
			Map.of("java.util.Optional", 0));
		assertEquals(catalog, EnumSuggestionGenerator.withOverrides(catalog, List.of(Custom.class)));
	}
}
