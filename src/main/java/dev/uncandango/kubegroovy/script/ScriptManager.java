package dev.uncandango.kubegroovy.script;

import com.google.gson.JsonObject;
import dev.uncandango.kubegroovy.DevProperties;
import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.plugin.ClassFilter;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugin;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugins;
import dev.uncandango.kubegroovy.typing.TypingBinding;
import dev.uncandango.kubegroovy.util.LogType;
import dev.uncandango.kubegroovy.util.RegistryAccessContainer;
import dev.uncandango.kubegroovy.web.local.KubeGroovyWeb;
import org.jspecify.annotations.NullUnmarked;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Loads and owns all [script packs][ScriptPack] for one [ScriptType].
///
/// On reload:
///   1. [#unload()] clears packs and event listeners through [ScriptType#unload()]
///   2. [#loadFromDirectory()] walks the filesystem and populates [ScriptPack] / [ScriptFile] objects
///   3. [#load(long)] creates the [KubeGroovyContextFactory], registers type wrappers from all plugins,
///     then evaluates every script file in priority order inside a single [KubeGroovyContext].
///
/// Subclasses may override [#loadAdditional()] to inject extra script sources
/// (e.g. `StartupScriptManager` adds `local/kubegroovy/local_startup_scripts`)
/// and define additional behaviour.
///
/// If `DevProperties.reloadOnFileSave` is set, a [KubeGroovyFileWatcherThread] is
/// started after load to trigger hot-reloads on file changes.
@NullUnmarked
public class ScriptManager {
	public static final List<String> SUPPORTED_EXTENSIONS = List.of(".groovy", ".gy", ".gvy");
	public final ScriptType scriptType;
	public final Map<String, ScriptPack> packs;
	private static volatile ClassFilter classFilter;

	public static synchronized void initializeClassFilter() {
		if (classFilter != null) throw new IllegalStateException("Class filter already initialized");
		classFilter = KubeGroovyPlugins.createClassFilter();
	}
	public KubeGroovyContextFactory contextFactory;
	public volatile KubeGroovyContext context;
	public boolean canListenEvents;
	private volatile Map<String, TypingBinding> typingBindings = Map.of();

	public ScriptManager(ScriptType t) {
		scriptType = t;
		packs = new LinkedHashMap<>();
	}

	/** Immutable descriptors captured during normal binding initialization. */
	public List<TypingBinding> getTypingBindings() {
		return List.copyOf(typingBindings.values());
	}

	public void captureTypingBindings(Map<String, Object> variables) {
		var snapshot = new LinkedHashMap<String, TypingBinding>();
		for (var entry : variables.entrySet()) {
			var descriptor = TypingBinding.describe(entry.getKey(), entry.getValue(), scriptType);
			if (descriptor != null) {
				snapshot.put(entry.getKey(), descriptor);
			}
		}
		typingBindings = Map.copyOf(snapshot);
	}

	public RegistryAccessContainer getRegistries() {
		return RegistryAccessContainer.current;
	}

	public void unload() {
		packs.clear();
		context = null;
		scriptType.unload();
	}

	public void reload() {
		long start = System.currentTimeMillis();

		KubeGroovyWeb.broadcastUpdate("before_scripts_loaded", "", () -> {
			var broadcast = new JsonObject();
			broadcast.addProperty("type", scriptType.name);
			broadcast.addProperty("time", start);
			return broadcast;
		});

		unload();
		scriptType.console.writeToFile(LogType.INIT, KubeGroovy.DISPLAY_NAME + "; MC " + KubeGroovy.MC_VERSION_NUMBER + " NeoForge");
		scriptType.console.writeToFile(LogType.INIT, "Loaded plugins:");

		for (var plugin : KubeGroovyPlugins.getAll()) {
			scriptType.console.writeToFile(LogType.INIT, "- " + plugin.getClass().getName());
		}

		KubeGroovyPlugins.forEachPlugin(this, KubeGroovyPlugin::beforeScriptsLoaded);
		loadFromDirectory();
		load(start);
		KubeGroovyPlugins.forEachPlugin(this, KubeGroovyPlugin::afterScriptsLoaded);
	}

	public void collectScripts(ScriptPack pack, Path dir, String path) {
		if (!path.isEmpty() && !path.endsWith("/")) {
			path += "/";
		}

		final var pathPrefix = path;

		try {
			for (var file : Files.walk(dir, 10, FileVisitOption.FOLLOW_LINKS).filter(Files::isRegularFile).toList()) {
				var fileName = dir.relativize(file).toString().replace(File.separatorChar, '/');

				if (fileName.endsWith(".groovy")) {
					pack.info.scripts.add(new ScriptFileInfo(pack.info, file, pathPrefix + fileName));
				}
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	public void loadPackFromDirectory(Path path, String name, boolean exampleFile) {
		if (Files.notExists(path)) {
			if (!exampleFile) {
				return;
			}

			try {
				Files.createDirectories(path);
			} catch (Exception ex) {
				scriptType.console.error("Failed to create script directory", ex);
			}

			try (var out = Files.newOutputStream(path.resolve("main.groovy"))) {
				out.write((
					"package " + scriptType.path.getFileName().toString() + "\n\n" +
					"""
					// Visit the wiki for more info - https://mods.uncandango.dev/kubegroovy/
					console.info('Hello, World! (Loaded\s""" + name + " example script)')\n\n").getBytes(StandardCharsets.UTF_8));
			} catch (Exception ex) {
				scriptType.console.error("Failed to write main.groovy", ex);
			}
		}

		var pack = new ScriptPack(this, new ScriptPackInfo(path.getFileName().toString(), ""));

		if (Files.exists(path)) {
			collectScripts(pack, path, "");

			for (var fileInfo : pack.info.scripts) {
				loadFile(pack, fileInfo);
			}
		}

		packs.put(pack.info.namespace, pack);
	}

	private void loadFile(ScriptPack pack, ScriptFileInfo fileInfo) {
		try {
			var file = new ScriptFile(pack, fileInfo);
			var skip = file.skipLoading();

			if (skip.isEmpty()) {
				pack.scripts.add(file);
			} else {
				scriptType.console.info("Skipped " + fileInfo.location + ": " + skip);
			}
		} catch (Throwable error) {
			scriptType.console.error("Failed to pre-load script file '" + fileInfo.location + "'", error);
		}
	}

	public void loadFromDirectory() {
		loadPackFromDirectory(scriptType.path, scriptType.name, true);
	}

	public boolean isClassAllowed(String name) {
		return getClassFilter().isAllowed(name);
	}

	public static ClassFilter getClassFilter() {
		var filter = classFilter;
		if (filter == null) throw new IllegalStateException("Class filter has not been initialized");
		return filter;
	}

	public static ClassFilter getClassFilterIfInitialized() {
		return classFilter;
	}

	private void load(long startAll) {
		contextFactory = new KubeGroovyContextFactory(this);
		scriptType.console.contextFactory = new WeakReference<>(contextFactory);

		if (PlatformWrapper.isGeneratingData()) {
			scriptType.console.info("Skipping KubeGroovy script loading (DataGen)");
			return;
		}

		canListenEvents = true;

		var typeWrappers = new TypeWrapperRegistry(scriptType, contextFactory.getTypeWrappers());
		// typeWrappers.removeAll();
		RecordDefaultsRegistry recordDefaults = record -> {
		};

		for (var plugin : KubeGroovyPlugins.getAll()) {
			plugin.registerTypeWrappers(typeWrappers);
			plugin.registerRecordDefaults(recordDefaults);
		}

		var cx = (KubeGroovyContext) contextFactory.createContext();
		context = cx;

		//ScopedValue.where(KubeGroovyContext.CURRENT, cx).run(() -> {
			var i = 0;
			var t = 0;
			var watchingFiles = new ArrayList<ScriptFile>();

			for (var pack : packs.values()) {
				try {
					pack.scripts.sort(null);

					for (var file : pack.scripts) {
						t++;
						var start = System.currentTimeMillis();

						try {
							file.load(cx);
							i++;
							scriptType.console.info("Loaded script " + file.info.location + " in " + (System.currentTimeMillis() - start) / 1000D + " s");
							watchingFiles.add(file);
						} catch (Throwable ex) {
							scriptType.console.error("", ex);
						}
					}
				} catch (Throwable ex) {
					scriptType.console.error("Failed to read script pack " + pack.info.namespace, ex);
				}
			}

			loadAdditional();
			long end = System.currentTimeMillis();
			long ms = end - startAll;

			scriptType.console.info("Loaded " + i + "/" + t + " KubeGroovy " + scriptType.name + " scripts in " + ms / 1000D + " s with " + scriptType.console.errors.size() + " errors and " + scriptType.console.warnings.size() + " warnings");
			canListenEvents = false;

			if (!watchingFiles.isEmpty() && DevProperties.get().reloadOnFileSave) {
				scriptType.fileWatcherThread = new KubeGroovyFileWatcherThread(scriptType, watchingFiles.toArray(new ScriptFile[0]), this::fullReload);
				scriptType.fileWatcherThread.start();
			}

			int t1 = t;
			int i1 = i;

			KubeGroovyWeb.broadcastUpdate("after_scripts_loaded", "", () -> {
				var broadcast = new JsonObject();
				broadcast.addProperty("type", scriptType.name);
				broadcast.addProperty("total", t1);
				broadcast.addProperty("successful", i1);
				broadcast.addProperty("errors", scriptType.console.errors.size());
				broadcast.addProperty("warnings", scriptType.console.warnings.size());
				broadcast.addProperty("time", end);
				broadcast.addProperty("duration", ms);
				return broadcast;
			});
		//});
	}

	public void loadAdditional() {
	}

	protected void fullReload() {
		KubeGroovy.PROXY.runInMainThread(this::reload);
	}

	public static boolean isScriptSource(String name) {
		return SUPPORTED_EXTENSIONS.stream().anyMatch(name::endsWith);
	}
}
