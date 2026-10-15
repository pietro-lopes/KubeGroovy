package dev.uncandango.kubegroovy.client;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.client.icon.ItemIconExporter;
import dev.uncandango.kubegroovy.command.KubeGroovyClientCommands;
import dev.uncandango.kubegroovy.typing.KubeGroovyTypingGenerator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Options shown by {@code /kubegroovy generate-typings}: where the JetBrains
 * config folder is, whether LivePlugin is installed there, and whether item and
 * fluid icons should be exported for the LivePlugin type suggestions.
 */
@SuppressWarnings("NotNullFieldNotInitialized") // lateinit fields
public class GenerateTypingsScreen extends Screen {
	public static final String LIVE_PLUGIN_URL = "https://plugins.jetbrains.com/plugin/7282-liveplugin";

	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFA0A0A0;
	private static final int GREEN = 0xFF55FF55;
	private static final int YELLOW = 0xFFFFFF55;
	private static final int RED = 0xFFFF5555;

	// Remembered for the rest of the game session so reopening keeps the user's choice.
	private static @Nullable String lastPath;
	private static boolean lastGenerateIcons = true;

	private final CommandSourceStack source;
	private EditBox pathBox;
	private Checkbox iconsBox;
	private Button livePluginButton;
	private List<Line> status = List.of();

	private record Line(String text, int color) {
	}

	public GenerateTypingsScreen(CommandSourceStack source) {
		super(Component.literal("Generate KubeGroovy Typings"));
		this.source = source;
	}

	@Override
	protected void init() {
		super.init();
		int left = this.width / 2 - 155;
		int top = Math.max(30, this.height / 2 - 100);

		var previous = pathBox == null ? lastPath : pathBox.getValue();
		pathBox = new EditBox(this.font, left, top + 12, 200, 20, Component.literal("JetBrains config folder"));
		pathBox.setMaxLength(4096);
		pathBox.setHint(Component.literal("JetBrains config folder"));
		pathBox.setValue(previous == null || previous.isBlank() ? KubeGroovyTypingGenerator.defaultJetBrainsConfigRoot().toString() : previous);
		pathBox.setResponder(value -> refreshStatus());
		addRenderableWidget(pathBox);

		var browse = addRenderableWidget(Button.builder(Component.literal("Browse..."), button -> browse()).bounds(left + 205, top + 12, 50, 20).build());
		browse.setTooltip(Tooltip.create(Component.literal("Choose the JetBrains config folder, an IDE config folder, or its live-plugins folder")));
		var detect = addRenderableWidget(Button.builder(Component.literal("Detect"), button -> pathBox.setValue(KubeGroovyTypingGenerator.defaultJetBrainsConfigRoot().toString())).bounds(left + 260, top + 12, 50, 20).build());
		detect.setTooltip(Tooltip.create(Component.literal("Reset to the JetBrains config folder detected on this machine")));

		livePluginButton = addRenderableWidget(Button.builder(Component.literal("Get LivePlugin"), ConfirmLinkScreen.confirmLink(this, LIVE_PLUGIN_URL, true)).bounds(left, top + 100, 150, 20).build());
		livePluginButton.setTooltip(Tooltip.create(Component.literal(LIVE_PLUGIN_URL)));
		addRenderableWidget(Button.builder(Component.literal("Recheck"), button -> refreshStatus()).bounds(left + 160, top + 100, 150, 20).build());

		iconsBox = addRenderableWidget(Checkbox.builder(Component.literal("Export item/fluid icons (%dpx) for type suggestions".formatted(ItemIconExporter.DEFAULT_SCALE)), this.font)
			.pos(left, top + 128)
			.selected(lastGenerateIcons)
			.onValueChange((checkbox, value) -> lastGenerateIcons = value)
			.maxWidth(310)
			.build());

		addRenderableWidget(Button.builder(Component.literal("Generate"), button -> generate()).bounds(left, top + 160, 150, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose()).bounds(left + 160, top + 160, 150, 20).build());

		setInitialFocus(pathBox);
		refreshStatus();
	}

	private @Nullable Path selectedPath() {
		var value = pathBox.getValue().strip();
		if (value.isEmpty()) return null;
		try {
			return Path.of(value).toAbsolutePath().normalize();
		} catch (InvalidPathException exception) {
			return null;
		}
	}

	private void refreshStatus() {
		var lines = new ArrayList<Line>();
		boolean livePluginFound = false;
		var path = selectedPath();
		if (path == null) {
			lines.add(new Line(pathBox.getValue().isBlank() ? "Enter the JetBrains config folder." : "Invalid folder path.", RED));
		} else if (!Files.isDirectory(path)) {
			lines.add(new Line("Folder not found. Is a JetBrains IDE installed?", RED));
		} else {
			var configs = KubeGroovyTypingGenerator.listIdeConfigs(path);
			if (!configs.isEmpty()) {
				lines.add(new Line("IDE configs: " + String.join(", ", configs.stream().map(KubeGroovyTypingGenerator.IdeConfig::name).toList()), WHITE));
			}
			try {
				var install = KubeGroovyTypingGenerator.findLivePluginInstallRoots(path);
				livePluginFound = !install.roots().isEmpty();
				if (livePluginFound) {
					var names = install.roots().stream().map(root -> root.getParent() == null ? root.toString() : String.valueOf(root.getParent().getFileName())).toList();
					lines.add(new Line("LivePlugin found: scripts will be installed into " + String.join(", ", names), GREEN));
				} else if (configs.stream().anyMatch(KubeGroovyTypingGenerator.IdeConfig::livePluginPlugin)) {
					lines.add(new Line("LivePlugin is installed but has no live-plugins folder yet. Open its tool window once, then Recheck.", YELLOW));
				} else {
					lines.add(new Line("LivePlugin is not installed. Typings are still generated, but the IDE scripts are skipped.", RED));
				}
			} catch (Exception exception) {
				KubeGroovy.LOGGER.debug("Could not inspect JetBrains folder {}", path, exception);
				lines.add(new Line("Could not read folder: " + exception.getMessage(), RED));
			}
		}
		status = List.copyOf(lines);
		livePluginButton.active = !livePluginFound;
	}

	private void browse() {
		var path = selectedPath();
		var start = path != null && Files.isDirectory(path) ? path.toString() : KubeGroovyTypingGenerator.defaultJetBrainsConfigRoot().toString();
		try {
			var chosen = TinyFileDialogs.tinyfd_selectFolderDialog("Select JetBrains config folder", start);
			if (chosen != null && !chosen.isBlank()) pathBox.setValue(chosen);
		} catch (Throwable throwable) {
			KubeGroovy.LOGGER.warn("Native folder picker is unavailable", throwable);
		}
	}

	private void generate() {
		var path = selectedPath();
		lastPath = pathBox.getValue();
		boolean icons = iconsBox.selected();
		minecraft.setScreen(null);
		KubeGroovyTypingGenerator.request(source, path);
		if (icons) KubeGroovyClientCommands.exportIcons(source, ItemIconExporter.DEFAULT_SCALE, null);
	}

	@Override
	public void onClose() {
		lastPath = pathBox.getValue();
		minecraft.setScreen(null);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int left = this.width / 2 - 155;
		int top = Math.max(30, this.height / 2 - 100);
		graphics.centeredText(this.font, this.title, this.width / 2, top - 20, WHITE);
		graphics.text(this.font, "JetBrains config folder", left, top, GRAY);
		int y = top + 40;
		for (var line : status) {
			for (var wrapped : this.font.split(Component.literal(line.text()), 310)) {
				if (y > top + 90) break;
				graphics.text(this.font, wrapped, left, y, line.color());
				y += 10;
			}
		}
		graphics.text(this.font, "Existing generated files are always replaced.", left, top + 146, GRAY);
	}
}
