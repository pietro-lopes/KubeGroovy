package dev.uncandango.kubegroovy.web.local;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.latvian.apps.tinyserver.ServerRegistry;
import dev.latvian.apps.tinyserver.content.MimeType;
import dev.latvian.apps.tinyserver.http.response.HTTPPayload;
import dev.latvian.apps.tinyserver.http.response.HTTPResponse;
import dev.latvian.apps.tinyserver.http.response.HTTPStatus;
import dev.latvian.apps.tinyserver.http.response.error.client.NotFoundError;
import dev.latvian.apps.tinyserver.http.response.error.server.InternalError;
import dev.latvian.apps.tinyserver.ws.Frame;
import dev.latvian.apps.tinyserver.ws.WSHandler;
import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.KubeGroovyPaths;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugin;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugins;
import dev.uncandango.kubegroovy.script.ScriptType;
import dev.uncandango.kubegroovy.script.data.GeneratedData;
import dev.uncandango.kubegroovy.util.RegExpKGY;
import dev.uncandango.kubegroovy.web.JsonContent;
import dev.uncandango.kubegroovy.web.KGYHTTPRequest;
import dev.uncandango.kubegroovy.web.KGYWSSession;
import dev.uncandango.kubegroovy.web.LocalWebServer;
import dev.uncandango.kubegroovy.web.LocalWebServerRegistry;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.tags.TagKey;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.resource.ResourcePackLoader;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class KubeGroovyWeb {
	private static @Nullable WSHandler<KGYHTTPRequest, KGYWSSession> UPDATES = null;

	private static final Map<String, Path> BROWSE = Map.of(
		"assets", KubeGroovyPaths.ASSETS,
		"data", KubeGroovyPaths.DATA,
		"startup_scripts", KubeGroovyPaths.STARTUP_SCRIPTS,
		"client_scripts", KubeGroovyPaths.CLIENT_SCRIPTS,
		"server_scripts", KubeGroovyPaths.SERVER_SCRIPTS,
		"logs", FMLPaths.GAMEDIR.get().resolve("logs")
	);

	public static int broadcastEvent(@Nullable WSHandler<?, ?> handler, String event, String requiredTag, @Nullable Supplier<JsonElement> payload) {
		if (handler == null || handler.sessions().isEmpty()) {
			return 0;
		}

		Frame frame = null;
		int count = 0;

		for (var s : handler.sessions().values()) {
			if (!requiredTag.isEmpty() && s instanceof KGYWSSession ks && !ks.info.tags().contains(requiredTag)) {
				continue;
			}

			if (frame == null) {
				var json = new JsonObject();
				json.addProperty("type", event);

				var p = payload == null ? null : payload.get();

				if (p != null && !(p instanceof JsonNull)) {
					json.add("payload", p);
				}

				frame = Frame.text(json.toString());
			}

			s.send(frame);
			count++;
		}

		return count;
	}

	public static int broadcastUpdate(String type, String requiredTag, Supplier<JsonElement> payload) {
		return broadcastEvent(UPDATES, type, requiredTag, payload);
	}

	public static void addScriptTypeEndpoints(ServerRegistry<KGYHTTPRequest> registry, ScriptType s, Runnable reload) {
		var path = "/api/console/" + s.name;

		s.console.wsBroadcaster = registry.ws(path + "/stream", (request) -> new ConsoleWSSession(request, s.console));

		registry.acceptPostString(path + "/info", s.console::info);
		registry.acceptPostString(path + "/warn", s.console::warn);
		registry.acceptPostString(path + "/error", s.console::error);
		registry.get(path + "/errors", s.console::getErrorsResponse);
		registry.get(path + "/warnings", s.console::getWarningsResponse);

		registry.acceptPostTask("/api/reload/" + s.name, reload);
	}

	public static void register(LocalWebServerRegistry registry) {
		UPDATES = registry.ws("/api/updates", KGYWSSession::new);

		registry.get("/", KubeGroovyWeb::getHomepage);
		registry.get("/api", KubeGroovyWeb::getAPIs);
		registry.get("/api/mods", KubeGroovyWeb::getMods);
		registry.get("/api/mods/{id}/icon", KubeGroovyWeb::getModIcon);

		registry.get("/api/assets.zip", KubeGroovyWeb::getAssetsZip);

		registry.get("/api/registries", KubeGroovyWeb::getRegistriesResponse); // List of all registries
		registry.get("/api/registries/{namespace}/{path}/keys", KubeGroovyWeb::getRegistryKeysResponse); // List of all IDs in registry
		registry.get("/api/registries/{namespace}/{path}/match/{regex}", KubeGroovyWeb::getRegistryMatchResponse); // List of RegEx matched IDs in registry

		registry.get("/api/tags/{namespace}/{path}", KubeGroovyWeb::getTagsResponse); // List of all tags in registry
		registry.get("/api/tags/{namespace}/{path}/values/{tag-namespace}/{tag-path}", KubeGroovyWeb::getTagValuesResponse); // List of all values in a tag
		registry.get("/api/tags/{namespace}/{path}/keys/{value-namespace}/{value-path}", KubeGroovyWeb::getTagKeysResponse); // List of all tags for a value
	}

	public static void registerWithAuth(LocalWebServerRegistry registry) {
		addScriptTypeEndpoints(registry, ScriptType.STARTUP, KubeGroovyWeb::reloadStartupScripts);
		addScriptTypeEndpoints(registry, ScriptType.SERVER, KubeGroovyWeb::reloadInternalServer);

		registry.get("/api/browse", KubeGroovyWeb::getBrowse);
		registry.get("/api/browse/{directory}", KubeGroovyWeb::getBrowseDir);
		registry.get("/api/browse/{directory}/<file>", KubeGroovyWeb::getBrowseFile);
	}

	public static void serverStarted(LocalWebServer instance) {
	}

	private static void reloadStartupScripts() {
		KubeGroovy.getStartupScriptManager().reload();
	}

	private static void reloadInternalServer() {
		var server = ServerLifecycleHooks.getCurrentServer();

		if (server != null) {
			server.kgy$runCommand("/reload");
		}
	}

	private static HTTPResponse getHomepage(KGYHTTPRequest req) {
		var list = new ArrayList<String>();
		list.add("KubeGroovy Local Web Server [" + KubeGroovy.PROXY.getWebServerWindowTitle() + "]");
		list.add(HTTPPayload.DATE_TIME_FORMATTER.format(req.startTime()));
		list.add("");

		list.add("Loaded Plugins:");

		for (var plugin : KubeGroovyPlugins.getAll()) {
			list.add("- " + plugin.getClass().getName());
		}

		list.add("");
		list.add("Loaded Mods:");

		for (var mod : ModList.get().getSortedMods()) {
			list.add("- " + mod.getModInfo().getDisplayName() + " (" + mod.getModId() + " - " + mod.getModInfo().getVersion() + ")");
		}

		list.add("");
		list.add("Available Endpoints:");

		for (var endpoint : LocalWebServer.instance().endpoints()) {
			list.add("- " + endpoint.method() + "\t" + endpoint.path() + (endpoint.auth() ? " [Requires Auth]" : ""));
		}

		list.add("");
		list.add("APIs:");

		KubeGroovyPlugins.forEachPlugin((id, version) -> list.add("- " + id + " v" + Math.max(version, 1)), KubeGroovyPlugin::registerLocalWebServerAPIs);

		return HTTPResponse.ok().text(list);
	}

	private static HTTPResponse getAPIs(KGYHTTPRequest req) {
		return HTTPResponse.ok().content(JsonContent.object(json -> KubeGroovyPlugins.forEachPlugin((id, version) -> json.addProperty(id.toString(), Math.max(version, 1)), KubeGroovyPlugin::registerLocalWebServerAPIs)));
	}

	private static HTTPResponse getMods(KGYHTTPRequest req) {
		return HTTPResponse.ok().content(JsonContent.array(json -> {
			for (var mod : ModList.get().getSortedMods()) {
				var o = new JsonObject();
				o.addProperty("id", mod.getModId());
				o.addProperty("name", mod.getModInfo().getDisplayName());
				o.addProperty("version", mod.getModInfo().getVersion().toString());
				json.add(o);
			}
		}));
	}

	private static HTTPResponse getModIcon(KGYHTTPRequest req) throws Exception {
		var mod = ModList.get().getModContainerById(req.variable("id").asString()).map(ModContainer::getModInfo).orElse(null);

		if (mod == null) {
			throw new NotFoundError("Mod not found");
		}

		var logo = mod.getLogoFile().orElse("");
		var img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);

		for (int i = 0; i < 128; i++) {
			for (int j = 0; j < 128; j++) {
				img.setRGB(i, j, 0xFF000000);
			}
		}

		if (!logo.isEmpty()) {
			var resourcePack = ResourcePackLoader.getPackFor(mod.getModId()).orElse(ResourcePackLoader.getPackFor("neoforge").orElseThrow(() -> new InternalError("Can't find neoforge, WHAT!")));

			try (var res = resourcePack.openPrimary(new PackLocationInfo("mod/" + mod.getModId(), Component.empty(), PackSource.BUILT_IN, Optional.empty()))) {
				var logoResource = res.getRootResource(logo.split("[/\\\\]"));

				if (logoResource != null) {
					var l = ImageIO.read(logoResource.get());
					var g = img.createGraphics();
					g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, mod.getLogoBlur() ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

					float r = l.getWidth() / (float) l.getHeight();
					int w, h;

					if (r > 1F) {
						w = 128;
						h = (int) (128 / r);
					} else {
						w = (int) (128 * r);
						h = 128;
					}

					g.drawImage(l, (128 - w) / 2, (128 - h) / 2, w, h, null);
					g.dispose();
				}
			}
		}

		return HTTPResponse.ok().png(img);
	}

	private static HTTPResponse getAssetsZip(KGYHTTPRequest req) throws IOException {
		if (Files.notExists(KubeGroovyPaths.ASSETS)) {
			throw new NotFoundError("kubegroovy/assets directory is not found!");
		}

		var allFiles = new HashMap<String, byte[]>();
		var allZipFiles = new HashMap<String, byte[]>();

		for (var rpath : Files.list(KubeGroovyPaths.ASSETS).sorted((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString())).toList()) {
			var fn = rpath.getFileName().toString();

			if (fn.endsWith(".zip")) {
				try (var fs = FileSystems.newFileSystem(rpath)) {
					var root = fs.getPath(".");

					for (var cpath : Files.walk(root).toList()) {
						if (Files.isRegularFile(cpath)) {
							var zpath = root.relativize(cpath).toString().replace('\\', '/');
							allZipFiles.put(zpath, Files.readAllBytes(cpath));
						}
					}
				}
			} else if (Files.isDirectory(rpath)) {
				for (var path : Files.walk(rpath).toList()) {
					var zpath = KubeGroovyPaths.DIRECTORY.relativize(path).toString().replace('\\', '/');

					if (Files.isRegularFile(path)) {
						allFiles.put(zpath, Files.readAllBytes(path));
					}
				}
			}
		}

		for (var entry : allZipFiles.entrySet()) {
			allFiles.putIfAbsent(entry.getKey(), entry.getValue());
		}

		allFiles.remove("LICENSE");
		allFiles.remove("pack.mcmeta");
		allFiles.remove("pack.png");

		var list = allFiles.entrySet().stream().sorted((a, b) -> a.getKey().compareToIgnoreCase(b.getKey())).toList();

		var zipBytes = new ByteArrayOutputStream();

		try (var out = new ZipOutputStream(zipBytes)) {
			for (var path : list) {
				out.putNextEntry(new ZipEntry(path.getKey()));
				out.write(path.getValue());
				out.closeEntry();
			}

			out.putNextEntry(new ZipEntry("pack.mcmeta"));
			out.write(GeneratedData.PACK_META.data().get());
			out.closeEntry();

			out.putNextEntry(new ZipEntry("pack.png"));
			out.write(GeneratedData.PACK_ICON.data().get());
			out.closeEntry();
		}

		return HTTPResponse.ok().content(zipBytes.toByteArray(), MimeType.ZIP).publicCache(Duration.ofSeconds(15L));
	}

	private static HTTPResponse getBrowse(KGYHTTPRequest req) {
		return HTTPResponse.ok().content(JsonContent.array(json -> BROWSE.keySet().forEach(json::add)));
	}

	private static HTTPResponse getBrowseDir(KGYHTTPRequest req) {
		var dirName = req.variable("directory").asString();
		var dir = BROWSE.get(dirName);

		if (dir == null) {
			return HTTPStatus.NOT_FOUND;
		}

		return HTTPResponse.ok().content(JsonContent.array(json -> {
			try {
				if (Files.exists(dir)) {
					for (var file : Files.walk(dir).filter(Files::isRegularFile).filter(Files::isReadable).toList()) {
						var fileName = file.getFileName().toString();

						if (fileName.endsWith(".gz") && dirName.equals("logs")) {
							continue;
						}

						var o = new JsonObject();
						o.addProperty("path", dir.relativize(file).toString().replace('\\', '/'));
						o.addProperty("name", fileName);
						o.addProperty("modified", Files.getLastModifiedTime(file).toMillis());
						json.add(o);
					}
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}));
	}

	private static HTTPResponse getBrowseFile(KGYHTTPRequest req) {
		var dir = BROWSE.get(req.variable("directory").asString());

		if (dir == null) {
			return HTTPStatus.NOT_FOUND;
		}

		var file = dir.resolve(req.variable("file").asString());

		if (Files.notExists(file)) {
			return HTTPStatus.NOT_FOUND;
		} else if (!Files.isRegularFile(file)) {
			return HTTPStatus.BAD_REQUEST;
		} else if (!Files.isReadable(file) || !file.startsWith(dir)) {
			return HTTPStatus.FORBIDDEN;
		} else {
			return HTTPResponse.ok().content(file);
		}
	}

	private static HTTPResponse getRegistriesResponse(KGYHTTPRequest req) {
		return HTTPResponse.ok().content(JsonContent.array(json -> {
			for (var registry : req.registries().registryAccess().registries().toList()) {
				json.add(registry.key().identifier().toString());
			}
		}));
	}

	private static HTTPResponse getRegistryKeysResponse(KGYHTTPRequest req) {
		var registry = req.registries().lookup(ResourceKey.createRegistryKey(req.id()));

		if (registry.isEmpty()) {
			return HTTPStatus.NOT_FOUND;
		}

		return HTTPResponse.ok().content(JsonContent.array(json -> {
			for (var key : registry.get().keySet()) {
				json.add(key.toString());
			}
		}));
	}

	private static HTTPResponse getRegistryMatchResponse(KGYHTTPRequest req) {
		var registry = req.registries().lookup(ResourceKey.createRegistryKey(req.id()));

		if (registry.isEmpty()) {
			return HTTPStatus.NOT_FOUND;
		}

		var regex = RegExpKGY.ofString(req.variable("regex").asString());

		if (regex == null) {
			return HTTPStatus.BAD_REQUEST;
		}

		return HTTPResponse.ok().content(JsonContent.array(json -> {
			for (var key : registry.get().keySet()) {
				var k = key.toString();

				if (regex.matcher(k).find()) {
					json.add(k);
				}
			}
		}));
	}

	private static HTTPResponse getTagsResponse(KGYHTTPRequest req) {
		var registry = req.registries().lookup(ResourceKey.createRegistryKey(req.id()));


		if (registry.isEmpty()) {
			return HTTPStatus.NOT_FOUND;
		}

		return HTTPResponse.ok().content(JsonContent.array(json -> {
			for (var tag : registry.get().listTagIds().map(TagKey::location).toList()) {
				json.add(tag.toString());
			}
		}));
	}

	private static HTTPResponse getTagValuesResponse(KGYHTTPRequest req) {
		var registry = req.registries().lookup(ResourceKey.createRegistryKey(req.id()));

		if (registry.isEmpty()) {
			return HTTPStatus.NOT_FOUND;
		}

		var tagId = TagKey.create(registry.get().key(), req.id("tag-namespace", "tag-path"));

		var tagKey = registry.get().getTags()
			.filter(t -> t.key().equals(tagId))
			.findFirst();

		if (tagKey.isEmpty()) {
			return HTTPStatus.NOT_FOUND;
		}

		return HTTPResponse.ok().content(JsonContent.array(json -> {
			for (var key : tagKey.get().stream().map(Holder::unwrapKey).filter(Optional::isPresent).map(Optional::get).map(ResourceKey::identifier).toList()) {
				json.add(key.toString());
			}
		}));
	}

	private static HTTPResponse getTagKeysResponse(KGYHTTPRequest req) {
		var registry = req.registries().lookup(ResourceKey.createRegistryKey(req.id()));

		if (registry.isEmpty()) {
			return HTTPStatus.NOT_FOUND;
		}

		var value = registry.get().get(req.id("value-namespace", "value-path"));

		if (value.isEmpty()) {
			return HTTPStatus.NOT_FOUND;
		}

		return HTTPResponse.ok().content(JsonContent.array(json -> {
			for (var key : value.get().tags().map(TagKey::location).toList()) {
				json.add(key.toString());
			}
		}));
	}
}
