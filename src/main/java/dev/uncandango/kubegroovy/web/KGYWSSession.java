package dev.uncandango.kubegroovy.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.latvian.apps.tinyserver.ws.WSSession;
import dev.uncandango.kubegroovy.util.JsonUtils;

public class KGYWSSession extends WSSession<KGYHTTPRequest> {
	public SessionInfo info = SessionInfo.NONE;
	public final KGYHTTPRequest request;

	public KGYWSSession(KGYHTTPRequest request) {
		this.request = request;
	}

	@Override
	public void onTextMessage(String message) {
		if (message.startsWith("{") && message.endsWith("}")) {
			var json = JsonUtils.fromString(message).getAsJsonObject();

			if (json.has("type")) {
				var type = json.get("type").getAsString();
				var payload = json.has("payload") ? json.get("payload") : JsonNull.INSTANCE;

				if (type.equals("$")) {
					if (payload instanceof JsonObject o) {
						info = SessionInfo.fromJson(info, o);
					}
				} else if (!type.isBlank()) {
					onEvent(type, payload);
				}
			}
		}
	}

	public void onEvent(String type, JsonElement payload) {
	}
}
