package dev.uncandango.kubegroovy.client.icon;

import com.mojang.blaze3d.platform.NativeImage;
import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.core.GameRendererKGY;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/// Renders item and fluid icons to PNG files, then overlays tag badges on the
/// corresponding saved member icons. Base rendering fills magic-color cells,
/// draws up to 64 stacks, flushes once, and screenshots the main target. An IO
/// worker crops, resizes, chroma-keys, and saves each cell. The capture technique
/// is adapted from IconExporter by CyclopsMC (MIT licensed); batching, tag composition, queue, error
/// accounting, and filename layout are KubeGroovy's own. Items draw through
/// `fakeItem`; fluids draw their still-texture sprite tinted by the model.
/// Client thread only: open through [ItemIconExportScreen#export], which
/// posts to the main thread. Never touch this from unit tests, it needs a
/// running client with a window.
public class ItemIconExportScreen extends Screen {
	/// Opaque magic background, keyed out to transparent on save. The unusual
	/// RGB value avoids blending with the world behind the GUI and is unlikely
	/// to occur in a real icon.
	private static final int BACKGROUND_COLOR = 0xFF12FE34;
	private static final int MAX_PENDING_BATCHES = 2;
	private static final long TAG_FRAME_BUDGET_NANOS = 4_000_000L;
	private static final long STALL_TIMEOUT_NANOS = 30_000_000_000L;
	private static final Identifier TAG_BADGE = Identifier.fromNamespaceAndPath("jei", "textures/gui/sprites/icons/tag_badge.png");
	private static volatile boolean running = false;

	public sealed interface ExportEntry permits Entry, FluidEntry {
		Identifier id();
	}

	public record Entry(Identifier id, ItemStack stack) implements ExportEntry {
	}

	public record FluidEntry(Identifier id, FluidStack fluid) implements ExportEntry {
	}

	public record Result(int exported, int failed, Path dir) {
	}

	private record CaptureCell(ExportEntry entry, int pixelX, int pixelY) {
	}

	private final ArrayDeque<ExportEntry> queue;
	private final ArrayDeque<ItemIconExporter.TagEntry> tagQueue;
	private final int total;
	private final Path outDir;
	private final int scale;
	private final Consumer<Component> feedback;
	private final @Nullable Consumer<Result> onDone;
	private final AtomicInteger exported = new AtomicInteger();
	private final AtomicInteger failed = new AtomicInteger();
	private final AtomicInteger inFlight = new AtomicInteger();
	private final AtomicInteger skippedTags = new AtomicInteger();
	private @Nullable NativeImage tagBadge;
	private @Nullable IOException tagBadgeLoadError;
	private boolean tagBadgeLoadAttempted = false;
	private volatile boolean finished = false;
	private final long startedNanos = System.nanoTime();
	private int pendingBatches = 0;
	private int captureCount = 0;
	private int lastReported = 0;
	private long stalledSinceNanos = 0;

	public static boolean isRunning() {
		return running && Minecraft.getInstance().screen instanceof ItemIconExportScreen;
	}

	/// Queues an export and opens the screen. Safe to call from anywhere, the
	/// actual open happens on the client thread. Refuses when an export is
	/// already running.
	public static void export(List<? extends ExportEntry> entries, Path outDir, int scale, Consumer<Component> feedback, @Nullable Consumer<Result> onDone) {
		export(entries, List.of(), outDir, scale, feedback, onDone);
	}

	/// Queues base icons first, then composites the supplied tag icons from the
	/// written PNGs after every base screenshot callback has completed.
	public static void export(List<? extends ExportEntry> entries, List<ItemIconExporter.TagEntry> tags, Path outDir, int scale, Consumer<Component> feedback, @Nullable Consumer<Result> onDone) {
		Minecraft.getInstance().execute(() -> {
			if (isRunning()) {
				feedback.accept(Component.literal("Icon export already running!"));
				return;
			}

			try {
				Files.createDirectories(outDir);
			} catch (Exception ex) {
				feedback.accept(Component.literal("Failed to create icon output dir: " + ex.getMessage()));
				return;
			}

			running = true;
			Minecraft.getInstance().setScreen(new ItemIconExportScreen(entries, tags, outDir, scale, feedback, onDone));
		});
	}

	private ItemIconExportScreen(List<? extends ExportEntry> entries, List<ItemIconExporter.TagEntry> tags, Path outDir, int scale, Consumer<Component> feedback, @Nullable Consumer<Result> onDone) {
		super(Component.literal("KubeGroovy Icon Export"));
		this.queue = new ArrayDeque<>(entries);
		this.tagQueue = new ArrayDeque<>(tags);
		this.total = entries.size() + tags.size();
		this.outDir = outDir;
		this.scale = scale;
		this.feedback = feedback;
		this.onDone = onDone;
	}

	/// The game must keep ticking while the screen is open (singleplayer
	/// would otherwise pause, which also stalls headless test waits).
	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void extractBlurredBackground(GuiGraphicsExtractor graphics) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		if (finished) {
			return;
		}

		reportProgress();
		if (inFlight.get() != 0 && (queue.isEmpty() || pendingBatches >= MAX_PENDING_BATCHES)) {
			long now = System.nanoTime();
			if (stalledSinceNanos == 0) {
				stalledSinceNanos = now;
			} else if (now - stalledSinceNanos > STALL_TIMEOUT_NANOS) {
				// Bound both GPU readbacks and PNG workers, including callbacks
				// that never arrive. Use elapsed time so a high frame rate cannot
				// time out a busy IO worker. Late completions cannot change the result.
				failRemaining();
				finish();
			}
			return;
		}

		stalledSinceNanos = 0;
		if (!queue.isEmpty()) {
			renderBatch(graphics);
		} else {
			long deadline = System.nanoTime() + TAG_FRAME_BUDGET_NANOS;
			for (int i = 0; i < IconExportBatch.MAX_ICONS && !tagQueue.isEmpty(); i++) {
				exportTag(tagQueue.remove());
				if (System.nanoTime() >= deadline) break;
			}
			if (tagQueue.isEmpty()) finish();
		}
		reportProgress();
	}

	private void reportProgress() {
		int done = exported.get() + failed.get() + skippedTags.get();

		if (done - lastReported >= 128) {
			lastReported = done;
			feedback.accept(Component.literal("Exported %d/%d icons...".formatted(done, total)));
		}
	}

	private void renderBatch(GuiGraphicsExtractor graphics) {
		var mc = Minecraft.getInstance();
		// The GUI renders in integer multiples of the natural 16-unit icon
		// size. Render the whole cell, then resize it to the requested output.
		int exportGuiScale = ItemIconExporter.renderGuiScale(scale);
		int renderSize = exportGuiScale * 16;
		var target = mc.getMainRenderTarget();
		var windowState = mc.gameRenderer.getGameRenderState().windowRenderState;
		var layout = IconExportBatch.fit(Math.min(target.width, windowState.width), Math.min(target.height, windowState.height), renderSize);
		if (layout.capacity() == 0) {
			feedback.accept(Component.literal("Window is too small to export icons at this size."));
			failRemaining();
			finish();
			return;
		}

		var cells = new ArrayList<CaptureCell>(layout.capacity());
		int originalGuiScale = windowState.guiScale;

		try {
			windowState.guiScale = exportGuiScale;
			for (int i = 0; i < layout.capacity() && !queue.isEmpty(); i++) {
				var entry = queue.remove();
				int pixelX = layout.pixelX(i);
				int pixelY = layout.pixelY(i);
				try {
					renderCell(graphics, entry, pixelX / exportGuiScale, pixelY / exportGuiScale);
					cells.add(new CaptureCell(entry, pixelX, pixelY));
				} catch (Exception ex) {
					failed.incrementAndGet();
					KubeGroovy.LOGGER.warn("Failed to render icon for {}: {}", entry.id(), ex.toString());
				}
			}
			if (cells.isEmpty()) {
				mc.gameRenderer.getGameRenderState().guiRenderState.reset();
				return;
			}

			var renderers = (GameRendererKGY) mc.gameRenderer;
			renderers.kgy$getGuiRenderer().render(renderers.kgy$getFogRenderer().getBuffer(FogRenderer.FogMode.NONE));
		} catch (Exception ex) {
			failed.addAndGet(cells.size());
			mc.gameRenderer.getGameRenderState().guiRenderState.reset();
			KubeGroovy.LOGGER.warn("Failed to render icon batch: {}", ex.toString());
			return;
		} finally {
			windowState.guiScale = originalGuiScale;
		}

		inFlight.addAndGet(cells.size());
		pendingBatches++;
		captureCount++;
		try {
			Screenshot.takeScreenshot(target, full -> {
				if (finished) {
					full.close();
					return;
				}
				try {
					Util.ioPool().execute(() -> saveBatch(mc, cells, full, renderSize));
				} catch (Exception ex) {
					full.close();
					mc.execute(() -> completeBatch(cells.size(), 0, cells.size()));
					KubeGroovy.LOGGER.warn("Failed to schedule icon batch save: {}", ex.toString());
				}
			});
		} catch (Exception ex) {
			completeBatch(cells.size(), 0, cells.size());
			KubeGroovy.LOGGER.warn("Failed to capture icon batch: {}", ex.toString());
		}
	}

	private static void renderCell(GuiGraphicsExtractor graphics, ExportEntry entry, int x, int y) {
		// Oversized models must not draw into a neighboring cell.
		graphics.enableScissor(x, y, x + 16, y + 16);
		try {
			graphics.fill(x, y, x + 16, y + 16, BACKGROUND_COLOR);
			if (entry instanceof FluidEntry fluid) {
				renderFluid(graphics, fluid.fluid(), x, y);
			} else if (entry instanceof Entry item) {
				graphics.fakeItem(item.stack(), x, y);
			}
		} finally {
			graphics.disableScissor();
		}
	}

	private void saveBatch(Minecraft mc, List<CaptureCell> cells, NativeImage full, int renderSize) {
		int saved = 0;
		int errors = 0;
		try {
			for (var cell : cells) {
				if (finished) break;
				try {
					saveIcon(cell, full, renderSize);
					saved++;
				} catch (Exception ex) {
					errors++;
					KubeGroovy.LOGGER.warn("Failed to save icon for {}: {}", cell.entry().id(), ex.toString());
				}
			}
		} finally {
			full.close();
		}
		int successful = saved;
		int unsuccessful = errors;
		mc.execute(() -> completeBatch(cells.size(), successful, unsuccessful));
	}

	/// All accounting stays on the client thread, including worker completions.
	private void completeBatch(int size, int saved, int errors) {
		if (finished) return;
		exported.addAndGet(saved);
		failed.addAndGet(errors);
		inFlight.addAndGet(-size);
		pendingBatches--;
		stalledSinceNanos = 0;
	}

	/// Draws the fluid's still texture stretched over the 16x16 export cell,
	/// tinted by its fluid model. Alpha is forced opaque so the
	/// chroma-keyed background never bleeds through translucent tints.
	private static void renderFluid(GuiGraphicsExtractor graphics, FluidStack stack, int x, int y) {
		var mc = Minecraft.getInstance();
		var fluidState = stack.getFluid().defaultFluidState();
		FluidModel fluidModel = mc.getModelManager().getFluidStateModelSet().get(fluidState);
		BlockTintSource tintSource = fluidModel.tintSource();
		int tint = (tintSource instanceof FluidTintSource fluidTintSource
			? fluidTintSource.colorAsStack(stack)
			: tintSource != null && mc.level != null && mc.player != null
				? tintSource.colorInWorld(fluidState.createLegacyBlock(), mc.level, mc.player.blockPosition())
				: tintSource != null ? tintSource.color(fluidState.createLegacyBlock()) : 0xFFFFFFFF) | 0xFF000000;
		var sprite = fluidModel.stillMaterial().sprite();
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, 16, 16, tint);
	}

	private void exportTag(ItemIconExporter.TagEntry entry) {
		try {
			Path baseFile = findBaseIcon(entry);

			if (baseFile == null) {
				skippedTags.incrementAndGet();
				return;
			}

			NativeImage badge = tagBadge();

			try (var base = NativeImage.read(Files.newInputStream(baseFile)); var result = new NativeImage(scale, scale, false)) {
				if (base.getWidth() != scale || base.getHeight() != scale) {
					throw new IOException("Base icon %s is %dx%d; expected %dx%d".formatted(baseFile, base.getWidth(), base.getHeight(), scale, scale));
				}

				if (badge.getWidth() != 9 || badge.getHeight() != 9) {
					throw new IOException("Tag badge is %dx%d; expected 9x9".formatted(badge.getWidth(), badge.getHeight()));
				}

				for (int y = 0; y < scale; y++) {
					int logicalY = (int) ((y + 0.5D) * 16 / scale);

					for (int x = 0; x < scale; x++) {
						int pixel = base.getPixel(x, y);
						int logicalX = (int) ((x + 0.5D) * 16 / scale);

						if (scale == 1) {
							// At 1px, the full-canvas center lands on the transparent
							// bottom edge of the badge. Sample its center so the overlay
							// remains visible at the minimum supported output size.
							pixel = sourceOver(pixel, badge.getPixel(4, 4));
						} else if (logicalX >= 7 && logicalY < 9) {
							pixel = sourceOver(pixel, badge.getPixel(logicalX - 7, logicalY));
						}

						result.setPixel(x, y, pixel);
					}
				}

				Path output = ItemIconExporter.tagResolve(outDir, entry);
				Files.createDirectories(output.getParent());
				result.writeToFile(output.toFile());
				exported.incrementAndGet();
			}
		} catch (Exception ex) {
			failed.incrementAndGet();
			KubeGroovy.LOGGER.warn("Failed to export tag icon for {} ({}): {}", entry.id(), entry.type().directory(), ex.toString());
		}
	}

	private Path findBaseIcon(ItemIconExporter.TagEntry entry) {
		for (Identifier member : entry.members()) {
			Path candidate = switch (entry.type()) {
				case FLUID -> ItemIconExporter.fluidResolve(outDir, member);
				case ITEM, BLOCK -> ItemIconExporter.resolve(outDir, member);
			};

			if (Files.isRegularFile(candidate)) {
				return candidate;
			}
		}

		return null;
	}

	private NativeImage tagBadge() throws IOException {
		if (!tagBadgeLoadAttempted) {
			tagBadgeLoadAttempted = true;
			var resource = Minecraft.getInstance().getResourceManager().getResource(TAG_BADGE);

			if (resource.isEmpty()) {
				tagBadgeLoadError = new IOException("Missing tag badge resource " + TAG_BADGE);
			} else {
				try {
					tagBadge = NativeImage.read(resource.get().open());
				} catch (IOException ex) {
					tagBadgeLoadError = ex;
				}
			}
		}

		if (tagBadgeLoadError != null) {
			throw new IOException("Unable to load tag badge", tagBadgeLoadError);
		}

		if (tagBadge == null) {
			throw new IOException("Unable to load tag badge " + TAG_BADGE);
		}

		return tagBadge;
	}

	private static int sourceOver(int destination, int source) {
		int sourceAlpha = source >>> 24;

		if (sourceAlpha == 0) {
			return destination;
		}

		int destinationAlpha = destination >>> 24;
		double destinationContribution = destinationAlpha * (255 - sourceAlpha) / 255.0D;
		int outputAlpha = (int) Math.round(sourceAlpha + destinationContribution);

		if (outputAlpha == 0) {
			return 0;
		}

		int red = blendChannel((source >>> 16) & 0xFF, (destination >>> 16) & 0xFF, sourceAlpha, destinationContribution, outputAlpha);
		int green = blendChannel((source >>> 8) & 0xFF, (destination >>> 8) & 0xFF, sourceAlpha, destinationContribution, outputAlpha);
		int blue = blendChannel(source & 0xFF, destination & 0xFF, sourceAlpha, destinationContribution, outputAlpha);
		return outputAlpha << 24 | red << 16 | green << 8 | blue;
	}

	private static int blendChannel(int source, int destination, int sourceAlpha, double destinationContribution, int outputAlpha) {
		return (int) Math.round((source * sourceAlpha + destination * destinationContribution) / outputAlpha);
	}

	private void saveIcon(CaptureCell cell, NativeImage full, int renderSize) throws IOException {
		if (full.getWidth() < cell.pixelX() + renderSize || full.getHeight() < cell.pixelY() + renderSize) {
			throw new IOException("Framebuffer %dx%d smaller than rendered icon %d".formatted(full.getWidth(), full.getHeight(), renderSize));
		}

		var file = ItemIconExporter.resolve(outDir, cell.entry());
		Files.createDirectories(file.getParent());

		try (var image = new NativeImage(scale, scale, false)) {
			for (int y = 0; y < scale; y++) {
				int sourceY = cell.pixelY() + (int) ((y + 0.5D) * renderSize / scale);

				for (int x = 0; x < scale; x++) {
					int sourceX = cell.pixelX() + (int) ((x + 0.5D) * renderSize / scale);
					int pixel = full.getPixel(sourceX, sourceY);
					image.setPixel(x, y, pixel == BACKGROUND_COLOR ? 0 : pixel);
				}
			}

			image.writeToFile(file.toFile());
		}
	}

	private void finish() {
		finish(true);
	}

	@Override
	public void removed() {
		if (!finished) {
			failRemaining();
			finish(false);
		}
	}

	private void failRemaining() {
		failed.addAndGet(queue.size() + tagQueue.size() + inFlight.getAndSet(0));
		queue.clear();
		tagQueue.clear();
		pendingBatches = 0;
	}

	private void finish(boolean closeScreen) {
		if (finished) {
			return;
		}

		finished = true;
		running = false;

		var result = new Result(exported.get(), failed.get(), outDir);
		int skipped = skippedTags.get();
		KubeGroovy.LOGGER.info("Icon export completed in {} ms ({} screenshot batches, {} icons, {} failures).",
			(System.nanoTime() - startedNanos) / 1_000_000L, captureCount, result.exported(), result.failed());
		feedback.accept(Component.literal("Exported %d icons (%d failed, %d tag%s skipped) to %s.".formatted(result.exported(), result.failed(), skipped, skipped == 1 ? "" : "s", outDir)));

		if (onDone != null) {
			try {
				onDone.accept(result);
			} catch (Exception ex) {
				KubeGroovy.LOGGER.warn("Icon export callback failed: {}", ex.toString());
			}
		}

		var mc = Minecraft.getInstance();

		if (closeScreen && mc.screen == this) {
			mc.setScreen(null);
		}

		if (tagBadge != null) {
			tagBadge.close();
			tagBadge = null;
		}
	}
}
