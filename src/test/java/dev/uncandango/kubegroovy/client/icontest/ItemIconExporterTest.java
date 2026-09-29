package dev.uncandango.kubegroovy.client.icontest;

import dev.uncandango.kubegroovy.client.icon.ItemIconExporter;
import dev.uncandango.kubegroovy.client.icon.ItemIconExporter.TagEntry;
import dev.uncandango.kubegroovy.client.icon.ItemIconExporter.TagType;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemIconExporterTest {
	@Test
	void scaleBounds() {
		assertEquals(32, ItemIconExporter.DEFAULT_SCALE);
		assertEquals(1, ItemIconExporter.MIN_SCALE);
		assertEquals(128, ItemIconExporter.MAX_SCALE);
		assertEquals(1, ItemIconExporter.clampScale(0));
		assertEquals(1, ItemIconExporter.clampScale(1));
		assertEquals(32, ItemIconExporter.clampScale(32));
		assertEquals(128, ItemIconExporter.clampScale(128));
		assertEquals(128, ItemIconExporter.clampScale(1000));
	}

	@Test
	void renderScaleCoversRequestedIconSize() {
		assertEquals(1, ItemIconExporter.renderGuiScale(1));
		assertEquals(1, ItemIconExporter.renderGuiScale(16));
		assertEquals(2, ItemIconExporter.renderGuiScale(17));
		assertEquals(2, ItemIconExporter.renderGuiScale(32));
		assertEquals(3, ItemIconExporter.renderGuiScale(33));
		assertEquals(8, ItemIconExporter.renderGuiScale(128));
	}

	@Test
	void exportLayout() {
		assertEquals(Path.of("item", "minecraft", "apple.png"), ItemIconExporter.relativePath(Identifier.parse("minecraft:apple")));
		assertEquals(Path.of("item", "minecraft", "block", "stone.png"), ItemIconExporter.relativePath(Identifier.parse("minecraft:block/stone")));
		assertEquals(Path.of("out").resolve(Path.of("item", "minecraft", "apple.png")), ItemIconExporter.resolve(Path.of("out"), Identifier.parse("minecraft:apple")));
		assertEquals(Path.of("export").resolve("icons-32"), ItemIconExporter.outputDir(Path.of("export"), 32));
		assertTrue(ItemIconExporter.outputDir(Path.of("export"), 32).toString().replace('\\', '/').endsWith("export/icons-32"));
	}

	@Test
	void fluidExportLayout() {
		assertEquals(Path.of("fluid", "minecraft", "water.png"), ItemIconExporter.fluidRelativePath(Identifier.parse("minecraft:water")));
		assertEquals(Path.of("fluid", "minecraft", "flowing_water.png"), ItemIconExporter.fluidRelativePath(Identifier.parse("minecraft:flowing_water")));
		assertEquals(Path.of("out").resolve(Path.of("fluid", "minecraft", "water.png")), ItemIconExporter.fluidResolve(Path.of("out"), Identifier.parse("minecraft:water")));
	}

	@Test
	void tagExportLayout() {
		var itemTag = new TagEntry(Identifier.parse("c:dyes/cyan"), TagType.ITEM, java.util.List.of(Identifier.parse("minecraft:cyan_dye")));
		var fluidTag = new TagEntry(Identifier.parse("c:water/overworld"), TagType.FLUID, java.util.List.of(Identifier.parse("minecraft:water")));
		var blockTag = new TagEntry(Identifier.parse("c:ores_in_ground/deepslate"), TagType.BLOCK, java.util.List.of(Identifier.parse("minecraft:deepslate_coal_ore")));

		assertEquals(Path.of("tags", "item", "c", "dyes", "cyan.png"), ItemIconExporter.tagRelativePath(itemTag));
		assertEquals(Path.of("tags", "fluid", "c", "water", "overworld.png"), ItemIconExporter.tagRelativePath(fluidTag));
		assertEquals(Path.of("tags", "block", "c", "ores_in_ground", "deepslate.png"), ItemIconExporter.tagRelativePath(blockTag));
		assertEquals(Path.of("out").resolve(ItemIconExporter.tagRelativePath(itemTag)), ItemIconExporter.tagResolve(Path.of("out"), itemTag));
	}
}
