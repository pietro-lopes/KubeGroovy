package dev.uncandango.kubegroovy.web;

import dev.latvian.apps.tinyserver.HTTPServer;
import net.minecraft.util.thread.BlockableEventLoop;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

public class KGYHTTPServer extends HTTPServer<KGYHTTPRequest> {
	record RequestFactory(BlockableEventLoop<?> eventLoop) implements Supplier<KGYHTTPRequest> {
		@Override
		public KGYHTTPRequest get() {
			return new KGYHTTPRequest(eventLoop);
		}
	}

	public final transient String auth;
	public final transient String encodedAuth;

	KGYHTTPServer(RequestFactory requestFactory, String auth) {
		super(requestFactory);
		this.auth = auth;
		this.encodedAuth = auth.isEmpty() ? "" : URLEncoder.encode(auth, StandardCharsets.UTF_8);
	}
}
