package dev.uncandango.kubegroovy.web;

import dev.latvian.apps.tinyserver.ServerRegistry;
import dev.latvian.apps.tinyserver.http.HTTPHandler;
import dev.latvian.apps.tinyserver.http.HTTPMethod;
import dev.latvian.apps.tinyserver.http.response.HTTPResponse;
import dev.latvian.apps.tinyserver.http.response.error.client.ForbiddenError;
import dev.latvian.apps.tinyserver.http.response.error.client.UnauthorizedError;
import dev.latvian.apps.tinyserver.ws.WSEndpointHandler;
import dev.latvian.apps.tinyserver.ws.WSHandler;
import dev.latvian.apps.tinyserver.ws.WSSession;
import dev.latvian.apps.tinyserver.ws.WSSessionFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LocalWebServerRegistry implements ServerRegistry<KGYHTTPRequest> {
	private record AuthHandler(HTTPHandler<KGYHTTPRequest> handler, String match) implements HTTPHandler<KGYHTTPRequest> {
		@Override
		public HTTPResponse handle(KGYHTTPRequest req) throws Exception {
			var a = req.header("Authorization").asString();

			if (a.isEmpty()) {
				throw new UnauthorizedError("Missing Authorization header");
			} else if (!a.equals(match)) {
				throw new ForbiddenError("Authorization header does not match configured auth token");
			}

			return handler.handle(req);
		}
	}

	private final KGYHTTPServer server;
	private final Set<LocalWebServer.Endpoint> endpoints;
	private final boolean requireAuth;

	LocalWebServerRegistry(KGYHTTPServer server, Set<LocalWebServer.Endpoint> endpoints, boolean requireAuth) {
		this.server = server;
		this.endpoints = endpoints;
		this.requireAuth = requireAuth;
	}

	private HTTPHandler<KGYHTTPRequest> wrap(HTTPHandler<KGYHTTPRequest> handler) {
		return requireAuth ? new AuthHandler(handler, "Bearer " + server.auth) : handler;
	}

	@Override
	public void http(HTTPMethod method, String path, HTTPHandler<KGYHTTPRequest> handler) {
		endpoints.add(new LocalWebServer.Endpoint(method.name(), path, requireAuth));
		server.http(method, path, wrap(handler));
	}

	@Override
	public <WSS extends WSSession<KGYHTTPRequest>> WSHandler<KGYHTTPRequest, WSS> ws(String path, WSSessionFactory<KGYHTTPRequest, WSS> factory) {
		endpoints.add(new LocalWebServer.Endpoint("WS", path, requireAuth));
		var handler = new WSEndpointHandler<>(factory, new ConcurrentHashMap<>());
		server.http(HTTPMethod.GET, path, wrap(handler));
		return handler;
	}
}
