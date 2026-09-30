package dev.uncandango.kubegroovy.typingtest;

import com.google.gson.JsonParser;
import dev.uncandango.kubegroovy.typing.KubeGroovyTypingGenerator;
import dev.uncandango.kubegroovy.plugin.TypeSuggestionCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RegistryBuilderCatalogTests {
	@Test
	void exportsDeterministicRegistryBuilderMetadata() {
		var block = new KubeGroovyTypingGenerator.LivePluginRegistryTarget("minecraft:block", "example.Block");
		var item = new KubeGroovyTypingGenerator.LivePluginRegistryTarget("minecraft:item", "example.Item");
		var detector = new KubeGroovyTypingGenerator.LivePluginBuilderType("minecraft:block", "kubegroovy:detector", "example.DetectorBlock.Builder", false);
		var defaultBlock = new KubeGroovyTypingGenerator.LivePluginBuilderType("minecraft:block", "kubegroovy:default", "example.BlockBuilder", true);
		var json = KubeGroovyTypingGenerator.renderEventMethodsJson(Map.of(), List.of(item, block), List.of(detector, defaultBlock));
		assertEquals(json, KubeGroovyTypingGenerator.renderEventMethodsJson(Map.of(), List.of(block, item, block), List.of(defaultBlock, detector, detector)));
		var root = JsonParser.parseString(json).getAsJsonObject();
		assertEquals(2, root.getAsJsonArray("_registryTargets").size());
		var builders = root.getAsJsonArray("_builderTypes");
		assertEquals(2, builders.size());
		assertTrue(builders.get(0).getAsJsonObject().get("defaultType").getAsBoolean());
		assertEquals("example.DetectorBlock.Builder", builders.get(1).getAsJsonObject().get("builderType").getAsString());
		assertEquals("minecraft:block", builders.get(1).getAsJsonObject().get("registryKey").getAsString());
	}

	@Test
	void suggestsNamedBuildersOnlyForTheirRegistry() {
		var block = new KubeGroovyTypingGenerator.LivePluginRegistryTarget("minecraft:block", "example.Block");
		var item = new KubeGroovyTypingGenerator.LivePluginRegistryTarget("minecraft:item", "example.Item");
		var detector = new KubeGroovyTypingGenerator.LivePluginBuilderType("minecraft:block", "kubegroovy:detector", "example.DetectorBlock.Builder", false);
		var defaultBlock = new KubeGroovyTypingGenerator.LivePluginBuilderType("minecraft:block", "kubegroovy:default", "example.BlockBuilder", true);
		var addon = new KubeGroovyTypingGenerator.LivePluginBuilderType("minecraft:item", "addon:special", "example.ItemBuilder", false);
		var json = KubeGroovyTypingGenerator.renderTypeSuggestionsJson(TypeSuggestionCatalog.empty(), List.of(block, item), List.of(detector, defaultBlock, addon));
		assertEquals(json, KubeGroovyTypingGenerator.renderTypeSuggestionsJson(TypeSuggestionCatalog.empty(), List.of(item, block), List.of(addon, detector, detector, defaultBlock)));
		var suggestions = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("registryBuilderTypes");
		assertEquals("[\"detector\",\"kubegroovy:detector\"]", suggestions.getAsJsonArray("example.Block").toString());
		assertEquals("[\"addon:special\"]", suggestions.getAsJsonArray("example.Item").toString());
		var ambiguous = new KubeGroovyTypingGenerator.LivePluginRegistryTarget("addon:block", "example.Block");
		var ambiguousJson = KubeGroovyTypingGenerator.renderTypeSuggestionsJson(TypeSuggestionCatalog.empty(), List.of(block, ambiguous), List.of(detector));
		assertFalse(JsonParser.parseString(ambiguousJson).getAsJsonObject().getAsJsonObject("registryBuilderTypes").has("example.Block"));
	}
}