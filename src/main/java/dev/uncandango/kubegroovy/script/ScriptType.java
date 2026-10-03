package dev.uncandango.kubegroovy.script;

import dev.uncandango.kubegroovy.KubeGroovyPaths;
import dev.uncandango.kubegroovy.event.EventGroups;
import dev.uncandango.kubegroovy.plugin.ClassFilter;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugins;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.NativeEventWrapper;
import dev.uncandango.kubegroovy.util.Lazy;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.loading.FMLPaths;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/// The three isolated script execution scopes in KubeGroovy.
///
///   - [#STARTUP] is loaded once during mod initialization; requires a full game restart to reload.
///     Used for registering content and doing other sorts of modifications that need to be done before a world load.
///   - [#SERVER] can be reloaded via `/reload`; used for recipes, loot tables, and server-side events.
///   - [#CLIENT] can be reloaded via F3+T (resource pack reload); used for tooltips, rendering, and client-side events.
///
/// Each type also has its own [ConsoleGY] instance for logging, as well as a [ClassFilter].
public enum ScriptType implements ScriptTypePredicate, ScriptTypeHolder {
	STARTUP("startup", "KubeGroovy Startup", KubeGroovyPaths.STARTUP_SCRIPTS),
	SERVER("server", "KubeGroovy Server", KubeGroovyPaths.SERVER_SCRIPTS),
	CLIENT("client", "KubeGroovy Client", KubeGroovyPaths.CLIENT_SCRIPTS);

	static {
		ConsoleGY.STARTUP = STARTUP.console;
		ConsoleGY.SERVER = SERVER.console;
		ConsoleGY.CLIENT = CLIENT.console;
	}

	public static final ScriptType[] VALUES = values();

	public final String name;
	public final ConsoleGY console;
	public final Path path;
	public final String nameStrip;
	public final ExecutorService executor;
	public final Lazy<ClassFilter> classFilter;
	public final Map<NativeEventWrapper.Listeners.Key<?>, NativeEventWrapper.Listeners<?>> nativeEventListeners;
	public @Nullable KubeGroovyFileWatcherThread fileWatcherThread;

	ScriptType(String n, String cname, Path path) {
		this.name = n;
		this.console = new ConsoleGY(this, LoggerFactory.getLogger(cname));
		this.path = path;
		this.nameStrip = name + "_scripts:";
		this.executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("KubeGroovy-" + n + "-bg-").factory());
		this.classFilter = Lazy.of(() -> KubeGroovyPlugins.createClassFilter(this));
		this.nativeEventListeners = new HashMap<>(0);
	}

	public Path getLogFile() {
		var dir = FMLPaths.GAMEDIR.get().resolve("logs/kubegroovy");
		var file = dir.resolve(name + ".log");

		try {
			if (!Files.exists(dir)) {
				Files.createDirectories(dir);
			}

			if (!Files.exists(file)) {
				var oldFile = dir.resolve(name + ".txt");

				if (Files.exists(oldFile)) {
					Files.move(oldFile, file);
				} else {
					Files.createFile(file);
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		return file;
	}

	public boolean isClient() {
		return this == CLIENT;
	}

	public boolean isServer() {
		return this == SERVER;
	}

	public boolean isStartup() {
		return this == STARTUP;
	}

	@HideFromGY
	public void unload() {
		console.resetFile();

		for (var group : EventGroups.ALL.get().map().values()) {
			for (var handler : group.getHandlers().values()) {
				handler.clear(this);
			}
		}

		for (var listener : nativeEventListeners.values()) {
			listener.listeners().clear();
		}

		fileWatcherThread = null;
	}

	@Override
	public boolean test(ScriptType type) {
		return type == this;
	}

	@Override
	public List<ScriptType> getValidTypes() {
		return List.of(this);
	}

	@Override
	public ScriptTypePredicate negate() {
		return switch (this) {
			case STARTUP -> ScriptTypePredicate.COMMON;
			case SERVER -> ScriptTypePredicate.STARTUP_OR_CLIENT;
			case CLIENT -> ScriptTypePredicate.STARTUP_OR_SERVER;
		};
	}

	@Override
	public ScriptType kgy$getScriptType() {
		return this;
	}
}