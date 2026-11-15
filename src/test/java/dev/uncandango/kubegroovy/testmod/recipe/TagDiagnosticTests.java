package dev.uncandango.kubegroovy.testmod.recipe;

import dev.uncandango.kubegroovy.DevProperties;
import dev.uncandango.kubegroovy.script.ConsoleGY;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagLoader;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ForEachTest(groups = "kubegroovy.recipe.event")
public class TagDiagnosticTests {
	@GameTest
	@EmptyTemplate
	@TestHolder(value = "tag_diagnostic_capture", description = "Missing tag references are captured only when enabled")
	static void tagDiagnosticCapture(final DynamicTest test) {
		test.onGameTest(helper -> {
			var properties = DevProperties.get();
			boolean previous = properties.logErroringTags;
			var id = Identifier.parse("testmod:diagnostic_missing_tag");
			var loader = new TagLoader<Object>((key, required) -> Optional.empty(), "tags/item");
			var entries = Map.of(id, List.of(new TagLoader.EntryWithSource(TagEntry.element(id), "diagnostic test")));
			ConsoleGY.SERVER.startCapturingErrors();
			try {
				properties.logErroringTags = false;
				loader.build(entries);
				helper.assertTrue(ConsoleGY.SERVER.errors.stream().noneMatch(line -> line.message.contains(id.toString())), "Disabled tag capture");
				properties.logErroringTags = true;
				loader.build(entries);
				helper.assertTrue(ConsoleGY.SERVER.errors.stream().anyMatch(line -> line.message.contains(id.toString()) && line.message.contains("diagnostic test")), "Tag error includes missing reference and source pack");
			} finally {
				properties.logErroringTags = previous;
				ConsoleGY.SERVER.errors.removeIf(line -> line.message.contains(id.toString()));
				ConsoleGY.SERVER.stopCapturingErrors();
			}
			helper.succeed();
		});
	}
}
