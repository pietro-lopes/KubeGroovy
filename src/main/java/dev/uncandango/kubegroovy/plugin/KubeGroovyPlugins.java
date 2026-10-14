package dev.uncandango.kubegroovy.plugin;

import dev.uncandango.kubegroovy.DevProperties;
import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.plugin.builtin.BuiltinKubeGroovyPlugin;
import dev.uncandango.kubegroovy.script.BindingRegistry;
import dev.uncandango.kubegroovy.util.ModResourceBindings;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.locating.IModFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

/// Discovers, loads, and stores all [KubeGroovyPlugin] instances.
/// Use [#forEachPlugin] to iterate over all loaded plugins.
public class KubeGroovyPlugins {
	private static final List<KubeGroovyPlugin> LIST = new ArrayList<>();
	private static final ModResourceBindings BINDINGS = new ModResourceBindings();

	/// Scans all mod JARs for plugin and binding definitions.
	///
	/// @see #loadMod(String, IModFile, boolean)
	public static void load(List<IModFile> modFiles, boolean loadClientPlugins) {
		try {
			for (var file : modFiles) {
				if (!file.getModInfos().isEmpty()) {
					loadMod(file.getModInfos().getFirst().getModId(), file, loadClientPlugins);
				}
			}
		} catch (Exception ex) {
			throw new RuntimeException("Failed to load KubeGroovy plugin", ex);
		}
	}

	/// Given a mod file, checks if it has a file defining KubeGroovy plugins or bindings,
	/// and tries to load them if they exist.
	///
	/// For plugin syntax, see [#loadFromFile(java.util.stream.Stream, java.lang.String, boolean)],
	/// and for bindings, see [ModResourceBindings]. Class rules are registered through plugins.
	private static void loadMod(String modId, IModFile mod, boolean loadClientPlugins) throws IOException {
		var contents = mod.getContents();

		var pluginData = contents.readFile("kubegroovy.plugins.txt");
		if (pluginData != null) {
			loadFromFile(new String(pluginData, StandardCharsets.UTF_8).lines(), modId, loadClientPlugins);
		}

		BINDINGS.readBindings(modId, mod);
	}

	/// Tries to load KubeGroovy plugins based on the contents of a `kubegroovy.plugins.txt` file.
	///
	/// A plugin definition consists of a FQCN referring to a class that implements [KubeGroovyPlugin],
	/// followed by an optional list of *mod ids* which are required for the plugin to be loaded.
	/// The string "client" may be used to ensure a plugin only loads on the client side.
	///
	/// Filters can be used to make sure that certain plugins only load if a mod is present.
	private static void loadFromFile(Stream<String> contents, String source, boolean loadClientPlugins) {
		KubeGroovy.LOGGER.info("Found plugin source {}", source);

		contents.map(s -> s.split("#", 2)[0].trim()) // allow comments (#)
			.filter(s -> !s.isBlank()) // filter empty lines
			.flatMap(s -> {
				String[] line = s.split(" ");

				for (int i = 1; i < line.length; i++) {
					if (line[i].equalsIgnoreCase("client")) {
						if (!loadClientPlugins) {
							if (DevProperties.get().logSkippedPlugins) {
								KubeGroovy.LOGGER.warn("Plugin " + line[0] + " does not load on server side, skipping");
							}

							return Stream.empty();
						}
					} else if (!ModList.get().isLoaded(line[i])) {
						if (DevProperties.get().logSkippedPlugins) {
							KubeGroovy.LOGGER.warn("Plugin " + line[0] + " does not have required mod '" + line[i] + "' loaded, skipping");
						}

						return Stream.empty();
					}
				}

				try {
					return Stream.of(Class.forName(line[0])); // try to load plugin class
				} catch (Throwable t) {
					KubeGroovy.LOGGER.error("Failed to load plugin {} from source {}: {}", s, source, t);
					t.printStackTrace();
					return Stream.empty();
				}
			})
			.filter(KubeGroovyPlugin.class::isAssignableFrom)
			.forEach(c -> {
				try {
					LIST.add((KubeGroovyPlugin) c.getDeclaredConstructor().newInstance()); // create the actual plugin instance
				} catch (Throwable t) {
					KubeGroovy.LOGGER.error("Failed to init KubeGroovy plugin {} from source {}: {}", c.getName(), source, t);
				}
			});
	}

	public static ClassFilter createClassFilter() {
		var filter = new ClassFilter();
		BuiltinKubeGroovyPlugin.registerSandboxClasses(filter);
		forEachPlugin(filter, KubeGroovyPlugin::registerClasses);
		filter.freeze();
		return filter;
	}

	public static void forEachPlugin(Consumer<KubeGroovyPlugin> callback) {
		LIST.forEach(callback);
	}

	public static <T> void forEachPlugin(T instance, BiConsumer<KubeGroovyPlugin, T> callback) {
		for (var item : LIST) {
			callback.accept(item, instance);
		}
	}

	public static List<KubeGroovyPlugin> getAll() {
		return Collections.unmodifiableList(LIST);
	}

	public static void addSidedBindings(BindingRegistry event) {
		BINDINGS.addBindings(event);
	}
}
