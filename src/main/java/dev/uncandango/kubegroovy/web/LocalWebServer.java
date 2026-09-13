package dev.uncandango.kubegroovy.web;

import dev.latvian.apps.tinyserver.error.BindFailedException;
import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugin;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugins;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import net.minecraft.util.thread.BlockableEventLoop;
import org.jspecify.annotations.Nullable;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public record LocalWebServer(KGYHTTPServer server, String url, List<Endpoint> endpoints, String explorerCode) {
	public record Endpoint(String method, String path, boolean auth) implements Comparable<Endpoint> {
		@Override
		public int compareTo(LocalWebServer.Endpoint o) {
			return path.compareToIgnoreCase(o.path);
		}
	}

	private static @Nullable LocalWebServer instance;

	@Nullable
	@HideFromGY
	public static LocalWebServer instance() {
		return instance;
	}

	@HideFromGY
	public static void start(BlockableEventLoop<?> eventLoop, boolean localClient) {
		if (instance == null) {
			try {
				var properties = WebServerProperties.get();
				var server = new KGYHTTPServer(new KGYHTTPServer.RequestFactory(eventLoop), properties.auth);

				var endpoints0 = new HashSet<LocalWebServer.Endpoint>();
				var registry = new LocalWebServerRegistry(server, endpoints0, false);
				var registryWithAuth = /*localClient || */server.auth.isEmpty() ? registry : new LocalWebServerRegistry(server, endpoints0, true);

				KubeGroovyPlugins.forEachPlugin(registry, KubeGroovyPlugin::registerLocalWebServer);
				KubeGroovyPlugins.forEachPlugin(registryWithAuth, KubeGroovyPlugin::registerLocalWebServerWithAuth);

				var publicAddress = localClient ? "" : properties.publicAddress;

				if (publicAddress.startsWith("https://")) {
					publicAddress = publicAddress.substring(8);
				} else if (publicAddress.startsWith("http://")) {
					publicAddress = publicAddress.substring(7);
				}

				server.setDaemon(true);
				server.setServerName(KubeGroovy.DISPLAY_NAME);
				server.setAddress(publicAddress.isEmpty() ? "127.0.0.1" : "0.0.0.0");
				server.setPortRange(properties.port, properties.port + 10);
				server.setMaxKeepAliveConnections(3);
				server.setKeepAliveTimeout(Duration.ofMinutes(5L));

				int port = server.start();
				var url = "http://localhost:" + port;
				var endpoints = new ArrayList<>(endpoints0);
				endpoints.sort(null);

				instance = new LocalWebServer(
					server,
					publicAddress.isEmpty() ? url : ("https://" + publicAddress),
					List.copyOf(endpoints),
					(publicAddress.isEmpty() ? ("p=" + port) : ("a=" + URLEncoder.encode(publicAddress, StandardCharsets.UTF_8))) + (server.auth.isEmpty() ? "" : ("&c=" + server.encodedAuth))
				);

				KubeGroovyPlugins.forEachPlugin(instance, KubeGroovyPlugin::localWebServerStarted);
				KubeGroovy.LOGGER.info("Started the local web server at {}", url);
			} catch (BindFailedException ex) {
				KubeGroovy.LOGGER.warn("Failed to start the local web server - all ports occupied");
			} catch (Exception ex) {
				KubeGroovy.LOGGER.warn("Failed to start the local web server - unexpected error");
				ex.printStackTrace();
			}
		}
	}
}
