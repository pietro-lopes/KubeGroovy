package dev.uncandango.kubegroovy.testmod.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.uncandango.kubegroovy.KubeGroovyPaths;
import dev.uncandango.kubegroovy.client.icon.ItemIconExportScreen;
import dev.uncandango.kubegroovy.client.icon.ItemIconExporter;
import dev.uncandango.kubegroovy.client.icon.ItemIconExporter.TagEntry;
import dev.uncandango.kubegroovy.client.icon.ItemIconExporter.TagType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertj;
import static org.assertj.core.api.Assertions.assertThat;

/// Java-side check for the item, fluid and tag icon export (`/kgy export-icons`,
/// see [ItemIconExportScreen]). Runs a fixed icon set through representative
/// output sizes and checks paths, dimensions, selection and overlay pixels.
/// Client-only: screenshots need the real client window, so this holder is
/// absent from `runGametest`.
@ForEachTest(groups = "kubegroovy.client.icon", side = Dist.CLIENT)
public class ItemIconExportTests {
	private static final List<Identifier> IDS = List.of(
		Identifier.parse("minecraft:stone"),
		Identifier.parse("minecraft:apple"),
		Identifier.parse("minecraft:diamond_sword"),
		Identifier.parse("minecraft:coal_ore"));

	private static final List<Identifier> FLUID_IDS = List.of(
		Identifier.parse("minecraft:water"),
		Identifier.parse("minecraft:lava"),
		Identifier.parse("kubegroovy:icon_test_tinted_fluid"));

	private static final int[] SCALES = {1, 16, 17, 32, 128};
	private static final int BATCH_ICON_COUNT = 65;

	private static final AtomicBoolean DONE = new AtomicBoolean(false);
	private static final AtomicReference<ItemIconExportScreen.Result> RESULT = new AtomicReference<>();
	private static final AtomicReference<String> COMPLETION = new AtomicReference<>();
	private static volatile Path dir = null;

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "icon_export", description = "Batched item, fluid and tag PNG export preserves pixels and isolates write failures")
	static void iconExport(final DynamicTest test) {
		test.onGameTest(helper -> {
			var sequence = helper.startSequence()
				.thenExecute(() -> assertj(helper, () -> {
					assertThat(Minecraft.getInstance().level).as("client level").isNotNull();
					assertThat(ItemIconExportScreen.isRunning()).as("no export running").isFalse();
					assertCollectedFluids();
					assertCollectedTags();
				}));

			for (int scale : SCALES) {
				sequence.thenExecute(() -> assertj(helper, () -> startExport(scale)))
					.thenWaitUntil(() -> assertj(helper, () -> assertExport(scale)));
			}

			sequence.thenExecute(() -> assertj(helper, ItemIconExportTests::startBatchedExport))
				.thenWaitUntil(() -> assertj(helper, ItemIconExportTests::assertBatchedExport));

			sequence.thenExecute(() -> assertj(helper, () -> startCrossNamespaceTagOnlyExport()))
				.thenWaitUntil(() -> assertj(helper, ItemIconExportTests::assertCrossNamespaceTagOnlyExport));

			sequence.thenExecute(() -> assertj(helper, () -> {
				closeLeftover();
				assertThat(ItemIconExportScreen.isRunning()).as("screen closed").isFalse();
			})).thenSucceed();
		});
	}

	private static void assertCollectedTags() {
		var allTags = ItemIconExporter.collectTagEntries(null);
		var kubeGroovyTags = ItemIconExporter.collectTagEntries("kubegroovy");
		assertThat(kubeGroovyTags).isNotEmpty().allMatch(entry -> entry.id().getNamespace().equals("kubegroovy"));
		assertThat(ItemIconExporter.collectTagEntries("minecraft")).allMatch(entry -> entry.id().getNamespace().equals("minecraft"));

		var nestedChild = findTag(kubeGroovyTags, TagType.ITEM, "kubegroovy:icon_export/item/nested_child");
		var nestedParent = findTag(kubeGroovyTags, TagType.ITEM, "kubegroovy:icon_export/item/nested_parent");
		var fallback = findTag(kubeGroovyTags, TagType.ITEM, "kubegroovy:icon_export/item/fallback");
		var empty = findTag(kubeGroovyTags, TagType.ITEM, "kubegroovy:icon_export/item/empty");
		var fluid = findTag(kubeGroovyTags, TagType.FLUID, "kubegroovy:icon_export/fluid/fixture");
		var block = findTag(kubeGroovyTags, TagType.BLOCK, "kubegroovy:icon_export/block/fallback");

		assertThat(nestedChild.members()).containsExactly(Identifier.parse("minecraft:stone"));
		assertThat(nestedParent.members()).containsExactly(Identifier.parse("minecraft:stone"), Identifier.parse("minecraft:apple"));
		assertThat(fallback.members()).containsExactly(Identifier.parse("minecraft:diamond"), Identifier.parse("minecraft:stone"));
		assertThat(empty.members()).isEmpty();
		assertThat(fluid.members()).containsExactly(Identifier.parse("minecraft:water"), Identifier.parse("minecraft:lava"));
		assertThat(block.members()).containsExactly(Identifier.parse("minecraft:coal_ore"));
		assertThat(allTags).contains(nestedParent, fallback, empty, fluid, block);
	}

	private static TagEntry findTag(List<TagEntry> tags, TagType type, String id) {
		return tags.stream()
			.filter(entry -> entry.type() == type && entry.id().equals(Identifier.parse(id)))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Missing tag " + type + " " + id));
	}

	private static void assertCollectedFluids() {
		var allFluids = ItemIconExporter.collectFluidEntries(null);
		assertThat(allFluids).hasSize(BuiltInRegistries.FLUID.size() - 1);
		assertThat(allFluids.stream().map(ItemIconExportScreen.FluidEntry::id).toList()).doesNotHaveDuplicates();
		assertThat(allFluids).allMatch(entry -> !entry.fluid().isEmpty());

		var minecraftFluids = ItemIconExporter.collectFluidEntries("minecraft");
		var minecraftIds = minecraftFluids.stream().map(ItemIconExportScreen.FluidEntry::id).toList();
		assertThat(minecraftIds).contains(
			Identifier.parse("minecraft:water"),
			Identifier.parse("minecraft:lava"),
			Identifier.parse("minecraft:flowing_water"),
			Identifier.parse("minecraft:flowing_lava"));
		assertThat(minecraftFluids).allMatch(entry -> !entry.fluid().isEmpty());
		assertThat(minecraftIds).doesNotContain(BuiltInRegistries.FLUID.getKey(Fluids.EMPTY));

		var kubeGroovyFluids = ItemIconExporter.collectFluidEntries("kubegroovy");
		var kubeGroovyIds = kubeGroovyFluids.stream().map(ItemIconExportScreen.FluidEntry::id).toList();
		assertThat(kubeGroovyFluids).allMatch(entry -> entry.id().getNamespace().equals("kubegroovy"));
		assertThat(kubeGroovyIds).contains(
			Identifier.parse("kubegroovy:coverage_fluid"),
			Identifier.parse("kubegroovy:flowing_coverage_fluid"),
			Identifier.parse("kubegroovy:icon_test_tinted_fluid"),
			Identifier.parse("kubegroovy:flowing_icon_test_tinted_fluid"));

		var tintedStack = fluidStackOf(Identifier.parse("kubegroovy:icon_test_tinted_fluid"));
		var fluidModel = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(tintedStack.getFluid().defaultFluidState());
		assertThat(fluidModel.tintSource()).as("custom fluid uses NeoForge tint source").isInstanceOf(FluidTintSource.class);
		assertThat(((FluidTintSource) fluidModel.tintSource()).colorAsStack(tintedStack))
			.as("custom fluid stack tint")
			.isEqualTo(0xFF2244AA);
	}

	private static void startExport(int scale) {
		assertThat(ItemIconExportScreen.isRunning()).as("previous export finished").isFalse();
		dir = KubeGroovyPaths.dir(KubeGroovyPaths.EXPORT.resolve("icons-test").resolve("scale-" + scale));

		for (var id : IDS) {
			deleteQuietly(dir.resolve(ItemIconExporter.relativePath(id)));
		}

		for (var id : FLUID_IDS) {
			deleteQuietly(dir.resolve(ItemIconExporter.fluidRelativePath(id)));
		}

		for (var tag : exportTags()) {
			deleteQuietly(ItemIconExporter.tagResolve(dir, tag));
		}

		DONE.set(false);
		RESULT.set(null);
		COMPLETION.set(null);
		var entries = new ArrayList<ItemIconExportScreen.ExportEntry>();

		for (var id : IDS) {
			var stack = stackOf(id);
			assertThat(stack.isEmpty()).as("non-empty stack " + id).isFalse();
			entries.add(new ItemIconExportScreen.Entry(id, stack));
		}

		for (var id : FLUID_IDS) {
			var stack = fluidStackOf(id);
			assertThat(stack.isEmpty()).as("non-empty fluid " + id).isFalse();
			entries.add(new ItemIconExportScreen.FluidEntry(id, stack));
		}

		ItemIconExportScreen.export(entries, exportTags(), dir, scale, message -> COMPLETION.set(message.getString()), result -> {
			RESULT.set(result);
			DONE.set(true);
		});
	}

	private static List<TagEntry> exportTags() {
		var tags = ItemIconExporter.collectTagEntries("kubegroovy");
		return List.of(
			findTag(tags, TagType.ITEM, "kubegroovy:icon_export/item/nested_parent"),
			findTag(tags, TagType.ITEM, "kubegroovy:icon_export/item/fallback"),
			findTag(tags, TagType.ITEM, "kubegroovy:icon_export/item/empty"),
			findTag(tags, TagType.FLUID, "kubegroovy:icon_export/fluid/fixture"),
			findTag(tags, TagType.BLOCK, "kubegroovy:icon_export/block/fallback"));
	}

	private static Identifier batchId(int index) {
		return Identifier.parse("kubegroovy:icon_export_batch/cell_" + index);
	}

	private static TagEntry batchTag() {
		return new TagEntry(Identifier.parse("kubegroovy:icon_export/item/batched"), TagType.ITEM, List.of(batchId(BATCH_ICON_COUNT - 1)));
	}

	private static void startBatchedExport() {
		assertThat(ItemIconExportScreen.isRunning()).as("previous export finished").isFalse();
		dir = KubeGroovyPaths.dir(KubeGroovyPaths.EXPORT.resolve("icons-test").resolve("scale-128"));
		var entries = new ArrayList<ItemIconExportScreen.ExportEntry>();
		for (int i = 0; i < BATCH_ICON_COUNT; i++) {
			int kind = i % (IDS.size() + FLUID_IDS.size());
			var id = batchId(i);
			ItemIconExportScreen.ExportEntry entry = kind < IDS.size()
				? new ItemIconExportScreen.Entry(id, stackOf(IDS.get(kind)))
				: new ItemIconExportScreen.FluidEntry(id, fluidStackOf(FLUID_IDS.get(kind - IDS.size())));
			deleteQuietly(ItemIconExporter.resolve(dir, entry));
			entries.add(entry);
			if (i == 9) {
				// An unwritable destination in the middle of a batch must not prevent
				// later cells or subsequent batches from saving successfully.
				var badId = Identifier.parse("kubegroovy:icon_export_batch/write_failure");
				Path badFile = ItemIconExporter.resolve(dir, badId);
				deleteQuietly(badFile);
				try {
					Files.createDirectories(badFile);
				} catch (Exception ex) {
					throw new AssertionError("Cannot prepare write-failure fixture", ex);
				}
				entries.add(new ItemIconExportScreen.Entry(badId, new ItemStack(Items.STONE)));
			}
		}
		deleteQuietly(ItemIconExporter.tagResolve(dir, batchTag()));
		DONE.set(false);
		RESULT.set(null);
		COMPLETION.set(null);
		ItemIconExportScreen.export(entries, List.of(batchTag()), dir, 128, message -> COMPLETION.set(message.getString()), result -> {
			RESULT.set(result);
			DONE.set(true);
		});
	}

	private static void assertBatchedExport() {
		assertThat(DONE.get()).as("mixed export crosses rows and screenshot batches").isTrue();
		assertThat(RESULT.get()).isNotNull();
		assertThat(RESULT.get().exported()).isEqualTo(BATCH_ICON_COUNT + 1);
		assertThat(RESULT.get().failed()).as("only the unwritable PNG fails").isEqualTo(1);
		for (int i = 0; i < BATCH_ICON_COUNT; i++) {
			int kind = i % (IDS.size() + FLUID_IDS.size());
			if (kind < IDS.size()) {
				Path file = ItemIconExporter.resolve(dir, batchId(i));
				assertPngWritten(file, batchId(i), 128, true);
				// Static item models must match their reference icon pixel for pixel,
				// including chroma-keyed transparency, regardless of their grid cell.
				try (var reference = NativeImage.read(readQuietly(ItemIconExporter.resolve(dir, IDS.get(kind))));
					 var actual = NativeImage.read(readQuietly(file))) {
					assertThat(actual.getPixelsABGR()).as("batch cell %d", i).isEqualTo(reference.getPixelsABGR());
				} catch (Exception ex) {
					throw new AssertionError("Failed comparing batch cell " + i, ex);
				}
			} else {
				// Fluid textures animate between captures; verify coverage and the
				// custom stack tint rather than comparing different animation frames.
				assertPngWritten(ItemIconExporter.fluidResolve(dir, batchId(i)), FLUID_IDS.get(kind - IDS.size()), 128, true);
			}
		}
		assertTagIcon(batchTag(), batchId(BATCH_ICON_COUNT - 1), 128);
	}

	private static void assertExport(int scale) {
		assertThat(DONE.get()).as("export finished at %dpx".formatted(scale)).isTrue();
		assertThat(RESULT.get()).as("export result at %dpx".formatted(scale)).isNotNull();
		assertThat(RESULT.get().exported()).as("successful item, fluid and tag icons at %dpx".formatted(scale)).isEqualTo(IDS.size() + FLUID_IDS.size() + 4);
		assertThat(RESULT.get().failed()).as("failed icons at %dpx".formatted(scale)).isZero();
		assertThat(COMPLETION.get()).as("completion summary at %dpx".formatted(scale)).contains("1 tag skipped");

		for (var id : IDS) {
			assertPngWritten(dir.resolve(ItemIconExporter.relativePath(id)), id, scale, false);
		}

		for (var id : FLUID_IDS) {
			assertPngWritten(dir.resolve(ItemIconExporter.fluidRelativePath(id)), id, scale, true);
		}

		var tags = exportTags();
		var nestedParent = tags.get(0);
		var fallback = tags.get(1);
		var empty = tags.get(2);
		var fluid = tags.get(3);
		var block = tags.get(4);
		assertThat(Files.exists(ItemIconExporter.tagResolve(dir, empty))).as("empty tag omitted").isFalse();
		assertTagIcon(nestedParent, Identifier.parse("minecraft:stone"), scale);
		assertTagIcon(fallback, Identifier.parse("minecraft:stone"), scale);
		assertTagIcon(fluid, Identifier.parse("minecraft:water"), scale);
		assertTagIcon(block, Identifier.parse("minecraft:coal_ore"), scale);
	}

	private static ItemStack stackOf(Identifier id) {
		return switch (id.toString()) {
			case "minecraft:stone" -> new ItemStack(Items.STONE);
			case "minecraft:apple" -> new ItemStack(Items.APPLE);
			case "minecraft:diamond_sword" -> new ItemStack(Items.DIAMOND_SWORD);
			case "minecraft:coal_ore" -> new ItemStack(Items.COAL_ORE);
			default -> ItemStack.EMPTY;
		};
	}

	private static void assertTagIcon(TagEntry tag, Identifier baseId, int scale) {
		Path tagFile = ItemIconExporter.tagResolve(dir, tag);
		Path baseFile = switch (tag.type()) {
			case FLUID -> dir.resolve(ItemIconExporter.fluidRelativePath(baseId));
			case ITEM, BLOCK -> dir.resolve(ItemIconExporter.relativePath(baseId));
		};
		assertThat(Files.exists(tagFile)).as("tag icon written " + tag.id()).isTrue();
		assertBadgeOverlay(baseFile, tagFile, scale);
	}

	private static void assertBadgeOverlay(Path baseFile, Path tagFile, int scale) {
		var resource = Minecraft.getInstance().getResourceManager().getResource(Identifier.fromNamespaceAndPath("jei", "textures/gui/sprites/icons/tag_badge.png")).orElseThrow();
		try (var base = NativeImage.read(readQuietly(baseFile));
			 var tagged = NativeImage.read(readQuietly(tagFile));
			 var badge = NativeImage.read(resource.open())) {
			assertThat(tagged.getWidth()).isEqualTo(scale);
			assertThat(tagged.getHeight()).isEqualTo(scale);
			assertThat(badge.getWidth()).isEqualTo(9);
			assertThat(badge.getHeight()).isEqualTo(9);
			int transparentSamples = 0;
			int badgeSamples = 0;
			int visibleBadgeSamples = 0;

			for (int y = 0; y < scale; y++) {
				int logicalY = (int) ((y + 0.5D) * 16 / scale);

				for (int x = 0; x < scale; x++) {
					int logicalX = (int) ((x + 0.5D) * 16 / scale);
					int basePixel = base.getPixel(x, y);
					int expected = basePixel;

					if (scale == 1 || (logicalX >= 7 && logicalY < 9)) {
						int badgePixel = scale == 1 ? badge.getPixel(4, 4) : badge.getPixel(logicalX - 7, logicalY);
						badgeSamples++;
						if ((badgePixel >>> 24) == 0) {
							transparentSamples++;
						} else {
							visibleBadgeSamples++;
						}
						expected = composite(basePixel, badgePixel);
					}

					assertThat(tagged.getPixel(x, y)).as("composited pixel %d,%d at scale %d".formatted(x, y, scale)).isEqualTo(expected);
				}
			}

			assertThat(badgeSamples).as("badge overlaps output at %dpx".formatted(scale)).isGreaterThan(0);
			if (scale > 1) {
				assertThat(transparentSamples).as("transparent badge pixels preserve the base at %dpx".formatted(scale)).isGreaterThan(0);
			}
			assertThat(visibleBadgeSamples).as("visible badge pixels at %dpx".formatted(scale)).isGreaterThan(0);
		} catch (Exception ex) {
			throw new AssertionError("Failed validating tag overlay at scale " + scale + ": " + ex);
		}
	}

	private static int composite(int base, int badge) {
		int sourceAlpha = badge >>> 24;
		if (sourceAlpha == 0) {
			return base;
		}

		int baseAlpha = base >>> 24;
		double baseContribution = baseAlpha * (255 - sourceAlpha) / 255.0D;
		int outAlpha = (int) Math.round(sourceAlpha + baseContribution);
		int red = blend((badge >>> 16) & 0xFF, (base >>> 16) & 0xFF, sourceAlpha, baseContribution, outAlpha);
		int green = blend((badge >>> 8) & 0xFF, (base >>> 8) & 0xFF, sourceAlpha, baseContribution, outAlpha);
		int blue = blend(badge & 0xFF, base & 0xFF, sourceAlpha, baseContribution, outAlpha);
		return outAlpha << 24 | red << 16 | green << 8 | blue;
	}

	private static int blend(int source, int base, int sourceAlpha, double baseContribution, int outAlpha) {
		return (int) Math.round((source * sourceAlpha + base * baseContribution) / outAlpha);
	}

	private static void startCrossNamespaceTagOnlyExport() {
		dir = KubeGroovyPaths.dir(KubeGroovyPaths.EXPORT.resolve("icons-test").resolve("scale-32"));
		var tag = new TagEntry(Identifier.parse("kubegroovy:icon_export/item/preexisting_cross_namespace"), TagType.ITEM, List.of(Identifier.parse("minecraft:stone")));
		deleteQuietly(ItemIconExporter.tagResolve(dir, tag));
		DONE.set(false);
		RESULT.set(null);
		COMPLETION.set(null);
		ItemIconExportScreen.export(List.of(), List.of(tag), dir, 32, message -> COMPLETION.set(message.getString()), result -> {
			RESULT.set(result);
			DONE.set(true);
		});
	}

	private static void assertCrossNamespaceTagOnlyExport() {
		assertThat(DONE.get()).as("tag-only export finished from a previous item PNG").isTrue();
		assertThat(RESULT.get()).isNotNull();
		assertThat(RESULT.get().exported()).isEqualTo(1);
		assertThat(RESULT.get().failed()).isZero();
		var tag = new TagEntry(Identifier.parse("kubegroovy:icon_export/item/preexisting_cross_namespace"), TagType.ITEM, List.of(Identifier.parse("minecraft:stone")));
		assertTagIcon(tag, Identifier.parse("minecraft:stone"), 32);
	}

	private static FluidStack fluidStackOf(Identifier id) {
		return switch (id.toString()) {
			case "minecraft:water" -> new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME);
			case "minecraft:lava" -> new FluidStack(Fluids.LAVA, FluidType.BUCKET_VOLUME);
			default -> {
				var fluid = BuiltInRegistries.FLUID.getValue(id);
				yield fluid == null ? FluidStack.EMPTY : new FluidStack(fluid, FluidType.BUCKET_VOLUME);
			}
		};
	}

	private static void assertPngWritten(Path file, Identifier id, int scale, boolean requireVisiblePixels) {
		assertThat(Files.exists(file)).as("icon written " + id).isTrue();

		try (var image = NativeImage.read(readQuietly(file))) {
			assertThat(image.getWidth()).as("width " + id).isEqualTo(scale);
			assertThat(image.getHeight()).as("height " + id).isEqualTo(scale);

			if (requireVisiblePixels) {
				int visiblePixels = 0;
				boolean hasExpectedCustomTint = !id.equals(Identifier.parse("kubegroovy:icon_test_tinted_fluid"));

				for (int y = 0; y < scale; y++) {
					for (int x = 0; x < scale; x++) {
						int pixel = image.getPixel(x, y);

						if ((pixel >>> 24) != 0) {
							visiblePixels++;

							if (id.equals(Identifier.parse("kubegroovy:icon_test_tinted_fluid"))) {
								int red = (pixel >> 16) & 0xFF;
								int green = (pixel >> 8) & 0xFF;
								int blue = pixel & 0xFF;
								hasExpectedCustomTint |= blue > red && blue > green;
							}
						}
					}
				}

				assertThat(visiblePixels).as("visible pixels " + id).isGreaterThan(0);
				assertThat(hasExpectedCustomTint).as("NeoForge stack tint applied " + id).isTrue();
			}
		} catch (Exception ex) {
			throw new AssertionError("Failed decoding icon " + id + " at " + file + ": " + ex);
		}
	}

	private static void deleteQuietly(Path file) {
		try {
			Files.deleteIfExists(file);
		} catch (Exception ex) {
			throw new AssertionError("Failed deleting " + file + ": " + ex);
		}
	}

	private static byte[] readQuietly(Path file) {
		try {
			return Files.readAllBytes(file);
		} catch (Exception ex) {
			throw new AssertionError("Failed reading " + file + ": " + ex);
		}
	}

	/// Best-effort cleanup so a failed export cannot wedge later holders with
	/// a stuck screen. Never masks the failure: the DONE assert above still runs.
	private static void closeLeftover() {
		try {
			var mc = Minecraft.getInstance();

			if (ItemIconExportScreen.isRunning()) {
				mc.execute(() -> {
					if (mc.screen instanceof ItemIconExportScreen) {
						mc.screen.onClose();
					}
				});
			}
		} catch (Exception ignored) {
		}
	}
}
