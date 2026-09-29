package dev.uncandango.kubegroovy.client.icon;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/// Collection and path helpers for item, fluid and tag icon exports. Rendering lives in
/// [ItemIconExportScreen]; the per-frame capture loop there is adapted from
/// IconExporter by CyclopsMC (MIT licensed): render one entry per frame,
/// screenshot the main target, crop, chroma-key the background, save.
public class ItemIconExporter {
	public static final int DEFAULT_SCALE = 32;
	public static final int MIN_SCALE = 1;
	public static final int MAX_SCALE = 128;

	private ItemIconExporter() {
	}

	public static int clampScale(int scale) {
		return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
	}

	/// GUI renders at integer multiples of Minecraft's 16-unit icon size.
	/// Round up so non-multiples still capture the complete icon before resize.
	public static int renderGuiScale(int scale) {
		return Math.max(1, (scale + 15) / 16);
	}

	/// Layout shared with the future LivePlugin lookup: `minecraft:apple`
	/// becomes `item/minecraft/apple.png` under the scale output dir. Fluids
	/// mirror it: `minecraft:water` becomes `fluid/minecraft/water.png`, so an
	/// item and a fluid with the same id never collide. Real directories, no
	/// escaping: identifier namespaces and paths are already valid path
	/// segments on every OS.
	public static Path relativePath(Identifier id) {
		return Path.of("item", id.getNamespace(), id.getPath() + ".png");
	}

	public static Path fluidRelativePath(Identifier id) {
		return Path.of("fluid", id.getNamespace(), id.getPath() + ".png");
	}

	public static Path tagRelativePath(TagEntry entry) {
		return Path.of("tags", entry.type().directory(), entry.id().getNamespace(), entry.id().getPath() + ".png");
	}

	public static Path resolve(Path dir, Identifier id) {
		return dir.resolve(relativePath(id));
	}

	public static Path fluidResolve(Path dir, Identifier id) {
		return dir.resolve(fluidRelativePath(id));
	}

	public static Path tagResolve(Path dir, TagEntry entry) {
		Path root = dir.normalize();
		Path file = root.resolve(tagRelativePath(entry)).normalize();

		if (!file.startsWith(root)) {
			throw new IllegalArgumentException("Tag icon path escapes output directory: " + entry.id());
		}

		return file;
	}

	/// Dispatches on the entry kind so items and fluids land in their own
	/// layout without the caller branching.
	public static Path resolve(Path dir, ItemIconExportScreen.ExportEntry entry) {
		return switch (entry) {
			case ItemIconExportScreen.FluidEntry fluid -> fluidResolve(dir, fluid.id());
			case ItemIconExportScreen.Entry item -> resolve(dir, item.id());
		};
	}

	public static Path outputDir(Path exportRoot, int scale) {
		return exportRoot.resolve("icons-" + scale);
	}

	/// Every item id exactly once, as its default instance. Needs the client
	/// registries, so call it on the client thread only.
	public static List<ItemIconExportScreen.Entry> collectEntries(@Nullable String namespace) {
		var entries = new ArrayList<ItemIconExportScreen.Entry>();

		for (Item item : BuiltInRegistries.ITEM) {
			var id = BuiltInRegistries.ITEM.getKey(item);

			if (namespace != null && !id.getNamespace().equals(namespace)) {
				continue;
			}

			var stack = item.getDefaultInstance();

			if (stack.isEmpty()) {
				continue;
			}

			entries.add(new ItemIconExportScreen.Entry(id, stack));
		}

		return entries;
	}

	/// Every fluid id exactly once, at bucket volume. Skips the empty fluid.
	/// Needs the client registries, so call it on the client thread only.
	public static List<ItemIconExportScreen.FluidEntry> collectFluidEntries(@Nullable String namespace) {
		var entries = new ArrayList<ItemIconExportScreen.FluidEntry>();

		for (Fluid fluid : BuiltInRegistries.FLUID) {
			if (fluid == Fluids.EMPTY) {
				continue;
			}

			var id = BuiltInRegistries.FLUID.getKey(fluid);

			if (namespace != null && !id.getNamespace().equals(namespace)) {
				continue;
			}

			var stack = new FluidStack(fluid, FluidType.BUCKET_VOLUME);

			if (stack.isEmpty()) {
				continue;
			}

			entries.add(new ItemIconExportScreen.FluidEntry(id, stack));
		}

		return entries;
	}

	/// Items followed by fluids, for the `/kgy export-icons` command.
	public static List<ItemIconExportScreen.ExportEntry> collectAllEntries(@Nullable String namespace) {
		var entries = new ArrayList<ItemIconExportScreen.ExportEntry>();
		entries.addAll(collectEntries(namespace));
		entries.addAll(collectFluidEntries(namespace));
		return entries;
	}

	/// Loaded item, fluid and block tags with their ordered, resolved members.
	/// Block members use their item variant when one exists. The namespace
	/// filter applies to tag ids, not to the namespaces of their members.
	public static List<TagEntry> collectTagEntries(@Nullable String namespace) {
		var entries = new ArrayList<TagEntry>();
		entries.addAll(collectTagEntries(BuiltInRegistries.ITEM, TagType.ITEM, namespace, BuiltInRegistries.ITEM::getKey));
		entries.addAll(collectTagEntries(BuiltInRegistries.FLUID, TagType.FLUID, namespace, BuiltInRegistries.FLUID::getKey));
		entries.addAll(collectTagEntries(BuiltInRegistries.BLOCK, TagType.BLOCK, namespace, ItemIconExporter::blockItemId));
		return entries;
	}

	private static @Nullable Identifier blockItemId(Block block) {
		Item item = block.asItem();
		return item == Items.AIR ? null : BuiltInRegistries.ITEM.getKey(item);
	}

	private static <T> List<TagEntry> collectTagEntries(Registry<T> registry, TagType type, @Nullable String namespace, Function<T, @Nullable Identifier> memberId) {
		var entries = new ArrayList<TagEntry>();

		for (HolderSet.Named<T> tag : registry.getTags().toList()) {
			Identifier id = tag.key().location();

			if (namespace != null && !id.getNamespace().equals(namespace)) {
				continue;
			}

			var members = new ArrayList<Identifier>();

			for (Holder<T> holder : tag) {
				Identifier candidate = memberId.apply(holder.value());

				if (candidate != null) {
					members.add(candidate);
				}
			}

			entries.add(new TagEntry(id, type, members));
		}

		return entries;
	}

	public enum TagType {
		ITEM("item"),
		FLUID("fluid"),
		BLOCK("block");

		private final String directory;

		TagType(String directory) {
			this.directory = directory;
		}

		public String directory() {
			return directory;
		}
	}

	public record TagEntry(Identifier id, TagType type, List<Identifier> members) {
		public TagEntry {
			members = List.copyOf(members);
		}
	}
}
